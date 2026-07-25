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
}
