package studios.littlesquad.welcomeflow.flow;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.LambdaActionFactory;
import studios.littlesquad.welcomeflow.config.ConfigIssue;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the validation claim: that a broken flows.yml produces messages an
 * owner can act on, naming the file, the exact YAML path and what was
 * expected.
 *
 * <p>Config validation is sold as a headline feature, so these tests assert
 * on the <em>content</em> of the messages, not merely that an error occurred.
 * A validator that says "invalid configuration" would pass a weaker test and
 * be worthless in practice.
 */
@DisplayName("flows.yml validation")
class FlowRegistryValidationTest {

    private ActionRegistry actions;
    private FlowRegistry registry;

    @BeforeEach
    void setUp() {
        actions = new ActionRegistry();
        // A no-op 'message' action is enough: these tests are about flow
        // structure, and unknown-action reporting is asserted separately.
        actions.register(new LambdaActionFactory("message", "message: <text>",
                spec -> {
                    spec.requireString("text", 0);
                    return context -> { };
                }));
        registry = new FlowRegistry(actions);
    }

    private List<ConfigIssue> load(String yaml) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        return registry.load(config.getConfigurationSection("flows"));
    }

    private static void assertMentions(List<ConfigIssue> issues, String... fragments) {
        String all = issues.stream().map(ConfigIssue::describe).reduce("", (a, b) -> a + "\n" + b);
        for (String fragment : fragments) {
            assertTrue(all.contains(fragment),
                    "expected an issue mentioning '" + fragment + "', got:" + all);
        }
    }

    @Nested
    @DisplayName("a valid flow")
    class Valid {

        @Test
        @DisplayName("loads with no issues")
        void validFlowLoadsCleanly() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        priority: 10
                        steps:
                          - id: move
                            display: "<white>Walk"
                            objective:
                              type: WALK_DISTANCE
                              amount: 25
                          - id: chop
                            display: "<white>Chop"
                            objective:
                              type: BREAK_BLOCK
                              material: OAK_LOG
                              amount: 5
                    """);

            assertTrue(issues.isEmpty(), "expected no issues, got: " + issues);
            assertEquals(1, registry.all().size());
            assertEquals(2, registry.byId("welcome").stepCount());
        }

        @Test
        @DisplayName("reports which objective types are in use, for lazy listeners")
        void reportsUsedObjectiveTypes() {
            load("""
                    flows:
                      welcome:
                        steps:
                          - id: a
                            objective: {type: WALK_DISTANCE, amount: 5}
                          - id: b
                            objective: {type: SEND_CHAT}
                    """);

            var used = registry.usedObjectiveTypes();
            assertEquals(2, used.size());
            assertTrue(used.contains(ObjectiveType.WALK_DISTANCE));
            assertTrue(used.contains(ObjectiveType.SEND_CHAT));
            assertFalse(used.contains(ObjectiveType.KILL_ENTITY),
                    "an unused type must not be reported, or we would register a listener for nothing");
        }
    }

    @Nested
    @DisplayName("structural errors")
    class Structural {

        @Test
        @DisplayName("duplicate step ids are named")
        void duplicateStepIds() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: move
                            objective: {type: SEND_CHAT}
                          - id: move
                            objective: {type: SEND_CHAT}
                    """);

            assertMentions(issues, "Duplicate step id", "move", "flows.welcome.steps[1].id");
        }

        @Test
        @DisplayName("a step without an id is named by its position")
        void missingStepId() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - objective: {type: SEND_CHAT}
                    """);

            assertMentions(issues, "flows.welcome.steps[0].id", "needs an 'id'");
        }

        @Test
        @DisplayName("a flow with no steps is rejected, not silently loaded")
        void flowWithoutSteps() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        priority: 5
                    """);

            assertMentions(issues, "flows.welcome.steps", "at least one step");
            assertTrue(registry.all().isEmpty(), "an unusable flow must not be registered");
        }

        @Test
        @DisplayName("a missing objective block is named")
        void missingObjective() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: move
                    """);

            assertMentions(issues, "flows.welcome.steps[0].objective", "objective");
        }
    }

    @Nested
    @DisplayName("invalid values")
    class InvalidValues {

        @Test
        @DisplayName("an unknown objective type lists the valid ones")
        void unknownObjectiveType() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: move
                            objective: {type: WALK_DISTANCEE, amount: 5}
                    """);

            assertMentions(issues, "WALK_DISTANCEE", "flows.welcome.steps[0].objective.type");
            // The message must show what IS valid, not just what isn't.
            assertMentions(issues, "WALK_DISTANCE");
        }

        @Test
        @DisplayName("an unknown material is named with the offending value")
        void unknownMaterial() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: chop
                            objective: {type: BREAK_BLOCK, material: OAK_LOGG, amount: 5}
                    """);

            assertMentions(issues, "OAK_LOGG", "flows.welcome.steps[0].objective.material", "Material");
        }

        @Test
        @DisplayName("an unknown entity type is named")
        void unknownEntity() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: hunt
                            objective: {type: KILL_ENTITY, entity: ZOMBIEE, amount: 3}
                    """);

            assertMentions(issues, "ZOMBIEE", "flows.welcome.steps[0].objective.entity");
        }

        @Test
        @DisplayName("a required key for the type is reported when absent")
        void missingRequiredKey() {
            // RUN_COMMAND needs 'pattern'.
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: kit
                            objective: {type: RUN_COMMAND}
                    """);

            assertMentions(issues, "RUN_COMMAND", "pattern");
        }

        @Test
        @DisplayName("an unparseable hint-after duration is named")
        void badDuration() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: move
                            hint-after: soon
                            objective: {type: SEND_CHAT}
                    """);

            assertMentions(issues, "hint-after", "soon");
        }

        @Test
        @DisplayName("a key the objective type ignores is a warning, not silence")
        void ignoredKeyIsWarned() {
            // SEND_CHAT has no material filter; a stray 'material' is almost
            // always a copy-paste leftover worth telling the owner about.
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: greet
                            objective: {type: SEND_CHAT, material: DIAMOND}
                    """);

            assertMentions(issues, "material", "ignores");
            assertTrue(issues.stream().anyMatch(issue -> !issue.isError()),
                    "an ignored key should warn rather than break the flow");
        }
    }

    @Nested
    @DisplayName("action errors")
    class Actions {

        @Test
        @DisplayName("an unknown action type lists the known ones")
        void unknownActionType() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        steps:
                          - id: move
                            objective: {type: SEND_CHAT}
                            on-step-complete:
                              - 'mesage: typo in the action name'
                    """);

            assertMentions(issues, "mesage", "flows.welcome.steps[0].on-step-complete[0]", "message");
        }

        @Test
        @DisplayName("a malformed action entry names its exact index")
        void malformedActionEntry() {
            List<ConfigIssue> issues = load("""
                    flows:
                      welcome:
                        on-flow-start:
                          - 'message: fine'
                          - 'this one has no colon'
                        steps:
                          - id: move
                            objective: {type: SEND_CHAT}
                    """);

            assertMentions(issues, "flows.welcome.on-flow-start[1]");
        }
    }

    @Nested
    @DisplayName("selection determinism")
    class Selection {

        @Test
        @DisplayName("flows are ordered by priority, then id, never by YAML order")
        void orderedByPriorityThenId() {
            load("""
                    flows:
                      zebra:
                        priority: 5
                        steps:
                          - id: a
                            objective: {type: SEND_CHAT}
                      alpha:
                        priority: 5
                        steps:
                          - id: a
                            objective: {type: SEND_CHAT}
                      important:
                        priority: 50
                        steps:
                          - id: a
                            objective: {type: SEND_CHAT}
                    """);

            List<String> order = registry.all().stream().map(Flow::id).toList();
            assertEquals(List.of("important", "alpha", "zebra"), order,
                    "equal priorities must tie-break on id so selection cannot depend on key ordering");
        }
    }
}
