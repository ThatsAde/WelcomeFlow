package studios.littlesquad.welcomeflow.flow;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.action.ActionParseException;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.ActionSpec;
import studios.littlesquad.welcomeflow.action.ActionTrigger;
import studios.littlesquad.welcomeflow.compat.Registries;
import studios.littlesquad.welcomeflow.config.ConfigIssue;
import studios.littlesquad.welcomeflow.objective.ObjectiveDefinition;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.util.Durations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses flows.yml into {@link Flow} objects and answers "which flow should
 * this player get".
 *
 * <p>Parsing collects every problem into a {@link ConfigIssue} list instead
 * of throwing on the first one, so an owner with three typos sees three
 * messages in one pass rather than fixing-and-restarting three times. A flow
 * with errors is skipped; the rest still load.
 */
public final class FlowRegistry {

    private static final String FILE = "flows.yml";

    private final ActionRegistry actionRegistry;

    private Map<String, Flow> flows = Map.of();
    private List<Flow> byPriority = List.of();
    private List<ConfigIssue> issues = List.of();

    public FlowRegistry(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    /**
     * Replaces the loaded flows with those defined in {@code root}.
     *
     * @param root the {@code flows} section of flows.yml, or {@code null}
     * @return every issue found; empty means a clean load
     */
    public List<ConfigIssue> load(ConfigurationSection root) {
        List<ConfigIssue> collected = new ArrayList<>();
        Map<String, Flow> parsed = new LinkedHashMap<>();

        if (root == null) {
            collected.add(ConfigIssue.error(FILE, "flows",
                    "No 'flows:' section found, so no onboarding will run. "
                            + "Restore the bundled flows.yml or add at least one flow."));
        } else {
            for (String flowId : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(flowId);
                if (section == null) {
                    collected.add(ConfigIssue.error(FILE, "flows." + flowId,
                            "Expected a flow definition block, but found a single value."));
                    continue;
                }
                int errorsBefore = countErrors(collected);
                Flow flow = parseFlow(flowId, section, collected);
                if (flow != null && countErrors(collected) == errorsBefore) {
                    parsed.put(flowId.toLowerCase(Locale.ROOT), flow);
                }
            }
            if (parsed.isEmpty() && countErrors(collected) == 0) {
                collected.add(ConfigIssue.warning(FILE, "flows",
                        "No flows are defined, so no player will be onboarded."));
            }
        }

        this.flows = Map.copyOf(parsed);
        // Highest priority first; id breaks ties so selection never depends on
        // YAML key ordering, which owners reorder freely.
        this.byPriority = parsed.values().stream()
                .sorted(Comparator.comparingInt(Flow::priority).reversed()
                        .thenComparing(Flow::id))
                .toList();
        this.issues = List.copyOf(collected);
        return this.issues;
    }

    private static int countErrors(List<ConfigIssue> issues) {
        return (int) issues.stream().filter(ConfigIssue::isError).count();
    }

    private Flow parseFlow(String flowId, ConfigurationSection section, List<ConfigIssue> issues) {
        String base = "flows." + flowId;
        int priority = section.getInt("priority", 0);

        Flow.Conditions conditions = parseConditions(section.getConfigurationSection("conditions"));
        Flow.Settings settings = parseSettings(section.getConfigurationSection("settings"));
        Map<ActionTrigger, List<Action>> flowActions = parseTriggers(section, base, issues);

        List<Step> steps = parseSteps(section, base, issues);
        if (steps.isEmpty()) {
            issues.add(ConfigIssue.error(FILE, base + ".steps",
                    "A flow needs at least one step; this flow will not be assigned to anyone."));
            return null;
        }

        return new Flow(flowId, priority, conditions, settings, steps, flowActions);
    }

    private Flow.Conditions parseConditions(ConfigurationSection section) {
        if (section == null) {
            return Flow.Conditions.permissive();
        }
        String previous = section.getString("previous-flow-completed", "");
        return new Flow.Conditions(
                section.getBoolean("first-join-only", false),
                section.getString("permission", ""),
                new HashSet<>(section.getStringList("worlds")),
                previous == null || previous.isBlank() ? null : previous.trim());
    }

    private Flow.Settings parseSettings(ConfigurationSection section) {
        if (section == null) {
            return Flow.Settings.defaults();
        }
        Flow.Settings defaults = Flow.Settings.defaults();
        return new Flow.Settings(
                section.getBoolean("resume-on-rejoin", defaults.resumeOnRejoin()),
                section.getInt("abandon-after-hours", defaults.abandonAfterHours()),
                section.getBoolean("show-checklist-bossbar", defaults.showChecklistBossbar()));
    }

    private List<Step> parseSteps(ConfigurationSection flowSection, String base, List<ConfigIssue> issues) {
        List<Map<?, ?>> rawSteps = flowSection.getMapList("steps");
        List<Step> steps = new ArrayList<>(rawSteps.size());
        Set<String> seenIds = new HashSet<>();

        for (int index = 0; index < rawSteps.size(); index++) {
            String path = base + ".steps[" + index + "]";
            Map<?, ?> raw = rawSteps.get(index);

            String id = stringValue(raw.get("id"));
            if (id == null || id.isBlank()) {
                issues.add(ConfigIssue.error(FILE, path + ".id",
                        "Every step needs an 'id'; it is used by /wf skip, the API and the stats funnel."));
                continue;
            }
            id = id.trim();
            if (!seenIds.add(id.toLowerCase(Locale.ROOT))) {
                issues.add(ConfigIssue.error(FILE, path + ".id",
                        "Duplicate step id '" + id + "' -- ids must be unique within a flow."));
                continue;
            }

            ObjectiveDefinition objective = parseObjective(raw.get("objective"), path, issues);
            if (objective == null) {
                continue;
            }

            long hintAfterMillis = 0L;
            String hintRaw = stringValue(raw.get("hint-after"));
            if (hintRaw != null && !hintRaw.isBlank()) {
                hintAfterMillis = Durations.parseMillis(hintRaw);
                if (hintAfterMillis < 0) {
                    issues.add(ConfigIssue.error(FILE, path + ".hint-after",
                            "Could not read the duration '" + hintRaw + "'. Use e.g. 45s, 2m or a tick count."));
                    hintAfterMillis = 0L;
                }
            }

            String display = stringValue(raw.get("display"));
            if (display == null || display.isBlank()) {
                display = id;
                issues.add(ConfigIssue.warning(FILE, path + ".display",
                        "No 'display' set; the checklist will show the raw step id '" + id + "'."));
            }

            steps.add(new Step(id, display, objective, hintAfterMillis,
                    parseTriggersFromMap(raw, path, issues)));
        }
        return steps;
    }

    private ObjectiveDefinition parseObjective(Object rawObjective, String stepPath, List<ConfigIssue> issues) {
        String path = stepPath + ".objective";
        if (!(rawObjective instanceof Map<?, ?> map)) {
            issues.add(ConfigIssue.error(FILE, path,
                    "Missing 'objective:' block. Every step needs one, e.g. "
                            + "objective:\n  type: BREAK_BLOCK\n  amount: 5"));
            return null;
        }

        String rawType = stringValue(map.get("type"));
        ObjectiveType type = ObjectiveType.fromConfig(rawType);
        if (type == null) {
            issues.add(ConfigIssue.error(FILE, path + ".type",
                    "Unknown objective type '" + rawType + "'. Valid types: "
                            + java.util.Arrays.toString(ObjectiveType.values())));
            return null;
        }

        int amount = intValue(map.get("amount"), 1);
        if (amount < 1) {
            issues.add(ConfigIssue.warning(FILE, path + ".amount",
                    "Amount " + amount + " is below 1; treating it as 1."));
            amount = 1;
        }

        // Flag keys that this type will ignore -- usually a copy-paste leftover.
        for (Object key : map.keySet()) {
            String name = String.valueOf(key);
            if (name.equals("type") || name.equals("amount")) {
                continue;
            }
            if (!type.allKeys().contains(name)) {
                issues.add(ConfigIssue.warning(FILE, path + "." + name,
                        "Objective type " + type + " ignores '" + name + "'. Valid extra keys: "
                                + (type.allKeys().isEmpty() ? "(none)" : type.allKeys())));
            }
        }
        for (String required : type.requiredKeys()) {
            if (stringValue(map.get(required)) == null) {
                issues.add(ConfigIssue.error(FILE, path + "." + required,
                        "Objective type " + type + " requires '" + required + "'."));
                return null;
            }
        }

        Material material = null;
        String materialName = stringValue(map.get("material"));
        if (materialName != null && !materialName.isBlank()) {
            material = Registries.material(materialName);
            if (material == null) {
                issues.add(ConfigIssue.error(FILE, path + ".material",
                        "Unknown material '" + materialName + "'. Use a Bukkit Material name, e.g. OAK_LOG."));
                return null;
            }
        }

        EntityType entityType = null;
        String entityName = stringValue(map.get("entity"));
        if (entityName != null && !entityName.isBlank()) {
            entityType = Registries.entityType(entityName);
            if (entityType == null) {
                issues.add(ConfigIssue.error(FILE, path + ".entity",
                        "Unknown entity type '" + entityName + "'. Use a Bukkit EntityType name, e.g. ZOMBIE."));
                return null;
            }
        }

        String pattern = stringValue(map.get("pattern"));
        String location = stringValue(map.get("location"));
        double radius = doubleValue(map.get("radius"), 3.0);
        if (type == ObjectiveType.REACH_LOCATION && radius <= 0) {
            issues.add(ConfigIssue.warning(FILE, path + ".radius",
                    "Radius must be positive; using 3."));
            radius = 3.0;
        }

        return new ObjectiveDefinition(type, amount, material, entityType, pattern, location, radius);
    }

    // --- action lists -----------------------------------------------------

    private Map<ActionTrigger, List<Action>> parseTriggers(ConfigurationSection section, String base,
                                                           List<ConfigIssue> issues) {
        Map<ActionTrigger, List<Action>> result = new EnumMap<>(ActionTrigger.class);
        for (ActionTrigger trigger : ActionTrigger.values()) {
            if (!section.contains(trigger.configKey())) {
                continue;
            }
            List<?> raw = section.getList(trigger.configKey());
            if (raw == null) {
                continue;
            }
            List<Action> actions = parseActionList(raw, base + "." + trigger.configKey(), issues);
            if (!actions.isEmpty()) {
                result.put(trigger, actions);
            }
        }
        return result;
    }

    private Map<ActionTrigger, List<Action>> parseTriggersFromMap(Map<?, ?> raw, String base,
                                                                 List<ConfigIssue> issues) {
        Map<ActionTrigger, List<Action>> result = new EnumMap<>(ActionTrigger.class);
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            ActionTrigger trigger = ActionTrigger.fromConfigKey(String.valueOf(entry.getKey()));
            if (trigger == null || !(entry.getValue() instanceof List<?> list)) {
                continue;
            }
            List<Action> actions = parseActionList(list, base + "." + trigger.configKey(), issues);
            if (!actions.isEmpty()) {
                result.put(trigger, actions);
            }
        }
        return result;
    }

    /** Parses a raw YAML action list, reporting and skipping bad entries. */
    public List<Action> parseActionList(List<?> raw, String basePath, List<ConfigIssue> issues) {
        List<Action> actions = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            String path = basePath + "[" + index + "]";
            Object entry = raw.get(index);
            try {
                ActionSpec spec;
                if (entry instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    spec = ActionSpec.ofMap(typed, path);
                } else {
                    spec = ActionSpec.ofString(String.valueOf(entry), path);
                }
                actions.add(actionRegistry.create(spec));
            } catch (ActionParseException e) {
                issues.add(ConfigIssue.error(FILE, path, e.getMessage()));
            }
        }
        return actions;
    }

    // --- selection --------------------------------------------------------

    /**
     * Picks the flow a joining player should start.
     *
     * <p>Deterministic: flows are considered highest-priority first with id as
     * the tie-break, and the first whose conditions match wins. Flows the
     * player has already completed are skipped so finishing a tutorial does
     * not immediately restart it.
     *
     * @return the chosen flow, or {@code null} when none apply
     */
    public Flow selectFor(Player player, PlayerSession session) {
        for (Flow flow : byPriority) {
            if (session.hasCompletedFlow(flow.id())) {
                continue;
            }
            if (flow.conditions().matches(player, session)) {
                return flow;
            }
        }
        return null;
    }

    /** Per-flow explanation of the selection outcome, for {@code debug: true}. */
    public List<String> explainSelection(Player player, PlayerSession session) {
        List<String> lines = new ArrayList<>(byPriority.size());
        for (Flow flow : byPriority) {
            if (session.hasCompletedFlow(flow.id())) {
                lines.add(flow.id() + " (priority " + flow.priority() + "): skipped, already completed");
                continue;
            }
            boolean matched = flow.conditions().matches(player, session);
            lines.add(flow.id() + " (priority " + flow.priority() + "): "
                    + (matched ? "MATCH" : "skipped, " + flow.conditions().explainMismatch(player, session)));
            if (matched) {
                break;
            }
        }
        return lines;
    }

    public Flow byId(String flowId) {
        return flowId == null ? null : flows.get(flowId.toLowerCase(Locale.ROOT));
    }

    public List<Flow> all() {
        return byPriority;
    }

    public Set<String> flowIds() {
        return flows.keySet();
    }

    public List<ConfigIssue> issues() {
        return issues;
    }

    /**
     * Objective types used by at least one loaded step. Drives lazy listener
     * registration: a server whose flows never craft anything should not pay
     * for a CraftItemEvent listener.
     */
    public Set<ObjectiveType> usedObjectiveTypes() {
        Set<ObjectiveType> used = java.util.EnumSet.noneOf(ObjectiveType.class);
        for (Flow flow : byPriority) {
            for (Step step : flow.steps()) {
                used.add(step.objective().type());
            }
        }
        return used;
    }

    // --- small YAML coercions ---------------------------------------------

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
