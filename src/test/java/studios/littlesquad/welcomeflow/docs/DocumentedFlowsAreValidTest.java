package studios.littlesquad.welcomeflow.docs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.LambdaActionFactory;
import studios.littlesquad.welcomeflow.config.ConfigIssue;
import studios.littlesquad.welcomeflow.flow.FlowRegistry;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses every YAML block in docs/CONFIG.md and the bundled flows.yml through
 * the real loader.
 *
 * <p>Documentation that does not work is worse than no documentation: a buyer
 * pastes an example, it fails, and their first impression of the plugin is
 * that its own docs are wrong. This test makes that impossible to ship.
 *
 * <p>It also means the examples cannot rot. Renaming an objective key or an
 * action type breaks this test, so the docs get updated in the same commit as
 * the code.
 */
@DisplayName("Documented flows")
class DocumentedFlowsAreValidTest {

    /** Fenced ```yaml blocks. */
    private static final Pattern YAML_BLOCK = Pattern.compile("```yaml\\s*\\n(.*?)```", Pattern.DOTALL);

    private static FlowRegistry newRegistry() {
        ActionRegistry actions = new ActionRegistry();
        // Register every real action type by name with a permissive parser.
        // The point here is to validate flow STRUCTURE and that no example
        // uses an action that does not exist; each action's own field parsing
        // is covered by its dedicated tests.
        for (String type : List.of("message", "broadcast", "actionbar", "title", "bossbar",
                "sound", "particle", "potion-effect", "teleport", "give-item",
                "open-book", "command", "delay", "conditional")) {
            actions.register(new LambdaActionFactory(type, type, spec -> context -> { }));
        }
        return new FlowRegistry(actions);
    }

    private static List<String> yamlBlocksOf(Path file) throws IOException {
        assertTrue(Files.exists(file), file + " is missing");
        Matcher matcher = YAML_BLOCK.matcher(Files.readString(file));
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }

    @TestFactory
    @DisplayName("every example in docs/CONFIG.md loads without errors")
    List<DynamicTest> documentedExamplesLoad() throws IOException {
        List<String> blocks = yamlBlocksOf(Path.of("docs", "CONFIG.md"));
        assertFalse(blocks.isEmpty(), "found no yaml blocks in docs/CONFIG.md; did the format change?");

        List<DynamicTest> tests = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            String yaml = blocks.get(index);
            // Only blocks that actually define flows are loadable; snippets
            // showing a single step in isolation are illustrative.
            if (!yaml.stripLeading().startsWith("flows:")) {
                continue;
            }
            String name = "docs/CONFIG.md yaml block #" + (index + 1);
            tests.add(DynamicTest.dynamicTest(name, () -> assertLoadsCleanly(yaml, name)));
        }
        assertFalse(tests.isEmpty(), "no flow definitions found in docs/CONFIG.md");
        return tests;
    }

    @TestFactory
    @DisplayName("the bundled flows.yml loads without errors")
    List<DynamicTest> bundledConfigLoads() throws IOException {
        Path bundled = Path.of("src", "main", "resources", "flows.yml");
        assertTrue(Files.exists(bundled), "bundled flows.yml is missing");
        String yaml = Files.readString(bundled);
        return List.of(DynamicTest.dynamicTest("src/main/resources/flows.yml",
                () -> assertLoadsCleanly(yaml, "bundled flows.yml")));
    }

    private static void assertLoadsCleanly(String yaml, String label) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        List<ConfigIssue> issues = newRegistry().load(config.getConfigurationSection("flows"));

        List<ConfigIssue> errors = issues.stream().filter(ConfigIssue::isError).toList();
        assertTrue(errors.isEmpty(), () -> label + " produced errors:\n"
                + errors.stream().map(ConfigIssue::describe).reduce("", (a, b) -> a + "  " + b + "\n"));

        // Warnings matter too here. A shipped example that warns teaches the
        // reader a bad habit and makes the plugin look sloppy on first run.
        List<ConfigIssue> warnings = issues.stream().filter(issue -> !issue.isError()).toList();
        assertTrue(warnings.isEmpty(), () -> label + " produced warnings:\n"
                + warnings.stream().map(ConfigIssue::describe).reduce("", (a, b) -> a + "  " + b + "\n"));
    }
}
