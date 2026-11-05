package studios.littlesquad.welcomeflow.config;

import net.kyori.adventure.text.minimessage.MiniMessage;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.flow.FlowRegistry;
import studios.littlesquad.welcomeflow.flow.Step;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import studios.littlesquad.welcomeflow.action.ActionSpec;

/**
 * Re-validates the whole configuration on demand and reports every problem
 * with file, path and an actionable message -- never a raw stacktrace.
 *
 * <p>Most structural validation happens inside {@link FlowRegistry} as a
 * by-product of parsing (unknown action types, bad materials, duplicate step
 * ids, unparseable durations), because a value has to be understood to be
 * loaded. This class re-runs that parse against the files on disk and adds
 * the checks that only make sense across the whole config:
 * <ul>
 *   <li>MiniMessage that will not parse</li>
 *   <li>flows referencing a {@code previous-flow-completed} that does not exist
 *       -- an unreachable flow</li>
 *   <li>named locations referenced by objectives but never defined</li>
 *   <li>soft-dependencies used in config but not installed</li>
 * </ul>
 *
 * <p>Full rule coverage lands with {@code /wf validate} in M4; the reload path
 * already uses what is here.
 */
public final class ConfigValidator {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ConfigManager configManager;
    private final FlowRegistry flowRegistry;
    private final ActionRegistry actionRegistry;

    public ConfigValidator(ConfigManager configManager, FlowRegistry flowRegistry, ActionRegistry actionRegistry) {
        this.configManager = configManager;
        this.flowRegistry = flowRegistry;
        this.actionRegistry = actionRegistry;
    }

    /**
     * Runs every rule against the currently loaded configuration.
     *
     * @return the problems found; empty means the configuration is sound
     */
    public List<ConfigIssue> validateAll() {
        List<ConfigIssue> issues = new ArrayList<>(flowRegistry.issues());
        validateCrossFlowReferences(issues);
        validateNamedLocations(issues);
        validateStepDisplays(issues);
        return List.copyOf(issues);
    }

    /**
     * Flags flows that can never be assigned because their prerequisite flow
     * does not exist. Silent unreachability is the worst kind of config bug:
     * everything loads, and nothing ever happens.
     */
    private void validateCrossFlowReferences(List<ConfigIssue> issues) {
        Set<String> knownIds = new HashSet<>();
        flowRegistry.all().forEach(flow -> knownIds.add(flow.id().toLowerCase(java.util.Locale.ROOT)));

        for (Flow flow : flowRegistry.all()) {
            String prerequisite = flow.conditions().previousFlowCompleted();
            if (prerequisite == null || prerequisite.isBlank()) {
                continue;
            }
            if (!knownIds.contains(prerequisite.toLowerCase(java.util.Locale.ROOT))) {
                issues.add(ConfigIssue.error("flows.yml",
                        "flows." + flow.id() + ".conditions.previous-flow-completed",
                        "References flow '" + prerequisite + "', which does not exist. "
                                + "This flow can never start. Known flows: " + knownIds + "."));
            }
            if (prerequisite.equalsIgnoreCase(flow.id())) {
                issues.add(ConfigIssue.error("flows.yml",
                        "flows." + flow.id() + ".conditions.previous-flow-completed",
                        "A flow cannot require itself as a prerequisite; it could never start."));
            }
            Set<String> visited = new HashSet<>();
            Flow current = flow;
            while (current != null && current.conditions().previousFlowCompleted() != null) {
                if (!visited.add(current.id().toLowerCase(Locale.ROOT))) {
                    issues.add(ConfigIssue.error("flows.yml", "flows." + flow.id() + ".conditions.previous-flow-completed",
                            "Circular flow prerequisites: " + visited + ". Remove one dependency."));
                    break;
                }
                current = flowRegistry.byId(current.conditions().previousFlowCompleted());
            }
        }
    }

    /**
     * Checks REACH_LOCATION objectives against the {@code locations} block.
     * {@code spawn} is exempt because it falls back to the main world's spawn.
     */
    private void validateNamedLocations(List<ConfigIssue> issues) {
        Set<String> defined = new HashSet<>();
        configManager.namedLocationKeys().forEach(key -> defined.add(key.toLowerCase(java.util.Locale.ROOT)));

        for (Flow flow : flowRegistry.all()) {
            for (int index = 0; index < flow.steps().size(); index++) {
                Step step = flow.steps().get(index);
                String locationName = step.objective().locationName();
                if (locationName == null || locationName.isBlank()) {
                    continue;
                }
                String normalised = locationName.toLowerCase(java.util.Locale.ROOT);
                if (defined.contains(normalised) || "spawn".equals(normalised)) {
                    continue;
                }
                issues.add(ConfigIssue.error("flows.yml",
                        "flows." + flow.id() + ".steps[" + index + "].objective.location",
                        "Unknown location '" + locationName + "'. Define it under 'locations:' in config.yml, "
                                + "or use 'spawn'. Defined: "
                                + (defined.isEmpty() ? "(none)" : defined) + "."));
            }
        }
    }

    /** Catches MiniMessage that will render as literal tag text to players. */
    private void validateStepDisplays(List<ConfigIssue> issues) {
        for (Flow flow : flowRegistry.all()) {
            for (int index = 0; index < flow.steps().size(); index++) {
                Step step = flow.steps().get(index);
                String path = "flows." + flow.id() + ".steps[" + index + "].display";
                checkMiniMessage(issues, "flows.yml", path, step.display());
            }
        }
    }

    private void checkMiniMessage(List<ConfigIssue> issues, String file, String path, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            MINI_MESSAGE.deserialize(raw);
        } catch (RuntimeException e) {
            issues.add(ConfigIssue.error(file, path,
                    "Invalid MiniMessage: " + e.getMessage()
                            + " -- check for an unclosed tag or a mistyped colour name."));
        }
    }

    /** Registered action types, for {@code /wf validate}'s summary output. */
    public List<String> knownActionTypes() {
        return actionRegistry.knownTypes();
    }

    /** Checks the complete documents, including text and global settings. */
    public List<ConfigIssue> validateDocuments(YamlConfiguration messages) {
        List<ConfigIssue> issues = new ArrayList<>();
        validateKeys(configManager.raw(), configManager.raw().getDefaults(), "config.yml", issues);
        validateKeys(messages, messages.getDefaults(), "messages.yml", issues);
        for (String path : messages.getKeys(true)) {
            if (messages.isString(path)) validateText(messages.getString(path), "messages.yml", path, issues);
        }
        walk(configManager.rawFlows(), "flows.yml", "", issues);
        walk(configManager.raw(), "config.yml", "", issues);
        for (String path : List.of("storage.periodic-flush-interval-seconds", "second-join.minimum-first-session-seconds",
                "return-bonus.minimum-hours-away", "return-bonus.cooldown-hours", "return-bonus.max-claims",
                "ui.title.default-fade-in-ticks", "ui.title.default-stay-ticks", "ui.title.default-fade-out-ticks")) {
            if (!configManager.raw().isInt(path) || configManager.raw().getInt(path) < 0)
                issues.add(ConfigIssue.error("config.yml", path, "Expected a non-negative integer."));
        }
        if (configManager.secondJoinTriggerJoinNumber() < 2)
            issues.add(ConfigIssue.error("config.yml", "second-join.trigger-join-number", "Expected an integer of at least 2."));
        if (!"sqlite".equalsIgnoreCase(configManager.storageType()))
            issues.add(ConfigIssue.error("config.yml", "storage.type", "Only sqlite is supported."));
        checkEnum(configManager.raw().getString("ui.bossbar.color", "BLUE"),
                Set.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE"), "ui.bossbar.color", issues);
        checkEnum(configManager.raw().getString("ui.bossbar.style", "SEGMENTED_10"),
                Set.of("PROGRESS", "SOLID", "SEGMENTED_6", "SEGMENTED_10", "SEGMENTED_12", "SEGMENTED_20",
                        "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20"), "ui.bossbar.style", issues);
        ConfigurationSection locations = configManager.raw().getConfigurationSection("locations");
        if (locations != null) for (String name : locations.getKeys(false)) {
            ConfigurationSection location = locations.getConfigurationSection(name);
            if (location == null) {
                issues.add(ConfigIssue.error("config.yml", "locations." + name, "Expected a location map."));
                continue;
            }
            for (String key : location.getKeys(false)) if (!Set.of("world", "x", "y", "z", "yaw", "pitch").contains(key))
                issues.add(ConfigIssue.warning("config.yml", "locations." + name + "." + key, "Unknown location key."));
            for (String coordinate : List.of("x", "y", "z")) if (!(location.get(coordinate) instanceof Number))
                issues.add(ConfigIssue.error("config.yml", "locations." + name + "." + coordinate, "Expected a numeric coordinate."));
            if (Bukkit.getWorld(location.getString("world", "")) == null)
                issues.add(ConfigIssue.warning("config.yml", "locations." + name + ".world", "World is not loaded."));
        }
        return List.copyOf(issues);
    }

    private void checkEnum(String value, Set<String> allowed, String path, List<ConfigIssue> issues) {
        if (!allowed.contains(value.toUpperCase(Locale.ROOT)))
            issues.add(ConfigIssue.error("config.yml", path, "Unknown value '" + value + "'. Expected " + allowed));
    }

    private void validateKeys(ConfigurationSection document, ConfigurationSection defaults, String file,
                              List<ConfigIssue> issues) {
        if (defaults == null) return;
        for (String path : document.getKeys(true)) {
            if (path.startsWith("locations.")) continue;
            Object value = document.get(path);
            Object expected = defaults.get(path);
            if (expected == null) issues.add(ConfigIssue.warning(file, path, "Unknown configuration key."));
            else if ((expected instanceof Boolean && !(value instanceof Boolean))
                    || (expected instanceof Number && !(value instanceof Number))
                    || (expected instanceof String && !(value instanceof String))
                    || (expected instanceof List<?> && !(value instanceof List<?>))
                    || (expected instanceof ConfigurationSection && !(value instanceof ConfigurationSection)))
                issues.add(ConfigIssue.error(file, path, "Wrong value type; follow the bundled configuration."));
        }
    }

    private void walk(Object value, String file, String path, List<ConfigIssue> issues) {
        if (value instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) walk(section.get(key), file, path.isEmpty() ? key : path + "." + key, issues);
        } else if (value instanceof Map<?, ?> map) {
            if (map.containsKey("type") && !path.contains("objective")) {
                @SuppressWarnings("unchecked") Map<String, Object> action = (Map<String, Object>) map;
                try { checkAction(ActionSpec.ofMap(action, path), file, path, issues); }
                catch (studios.littlesquad.welcomeflow.action.ActionParseException e) {
                    issues.add(ConfigIssue.error(file, path, e.getMessage()));
                }
            }
            map.forEach((key, child) -> walk(child, file, path + "." + key, issues));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) walk(list.get(i), file, path + "[" + i + "]", issues);
        } else if (value instanceof String text) {
            if (path.endsWith(".display") || path.endsWith(".text") || path.endsWith(".subtitle")
                    || path.endsWith(".display-name") || path.endsWith(".title")) validateText(text, file, path, issues);
            if (path.endsWith("]") && text.contains(":")) {
                try { checkAction(ActionSpec.ofString(text, path), file, path, issues); }
                catch (studios.littlesquad.welcomeflow.action.ActionParseException | RuntimeException e) {
                    issues.add(ConfigIssue.error(file, path, e.getMessage()));
                }
            }
            if (text.matches("(?s).*%[A-Za-z0-9]+_[^%]+%.*") && !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
                issues.add(ConfigIssue.warning(file, path, "PlaceholderAPI placeholders are used but PlaceholderAPI is not installed."));
        }
    }

    private void checkAction(ActionSpec spec, String file, String path, List<ConfigIssue> issues) {
        String type = spec.baseType();
        if (Set.of("message", "broadcast", "actionbar", "title", "bossbar").contains(type)) {
            validateText(spec.string("text", 0, ""), file, path, issues);
            if (type.equals("title")) validateText(spec.string("subtitle", 1, ""), file, path, issues);
        }
        if (type.equals("teleport") && spec.fieldCount() <= 1) {
            String location = spec.string("location", 0, "");
            if (!location.equalsIgnoreCase("spawn") && !configManager.namedLocationKeys().contains(location))
                issues.add(ConfigIssue.error(file, path, "Unknown named teleport location '" + location + "'."));
        }
        if (type.equals("command")) {
            String command = spec.string("command", 0, "").replaceFirst("^/", "").split("\\s+", 2)[0];
            if (!command.isEmpty() && !command.contains("<") && Bukkit.getCommandMap().getCommand(command) == null)
                issues.add(ConfigIssue.warning(file, path, "Command '" + command + "' is unavailable; install its providing plugin."));
        }
    }

    private static final Set<String> PLACEHOLDERS = Set.of("player", "flow", "step", "progress", "required", "percent",
            "time-away", "count", "description", "dropped", "errors", "file", "hint", "index", "joins", "label",
            "message", "name", "path", "state", "step-number", "step-total", "target", "total", "usage", "warnings");

    private void validateText(String text, String file, String path, List<ConfigIssue> issues) {
        checkMiniMessage(issues, file, path, text);
        // MiniMessage intentionally accepts unclosed styles. Check tag names without
        // rejecting the idiomatic '<green>Hello' used throughout shipped configs.
        var matcher = java.util.regex.Pattern.compile("(?<!\\\\)<(/?)([^<>]+)>").matcher(text);
        while (matcher.find()) {
            String body = matcher.group(2);
            String name = body.split(":", 2)[0].toLowerCase(Locale.ROOT);
            if (PLACEHOLDERS.contains(name)) continue;
            if (!net.kyori.adventure.text.minimessage.tag.standard.StandardTags.defaults().has(name)
                    && !name.matches("#[0-9a-f]{6}"))
                issues.add(ConfigIssue.warning(file, path, "Unknown MiniMessage tag <" + name + ">; it will render literally."));
        }
    }
}
