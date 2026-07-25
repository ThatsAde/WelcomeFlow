package studios.littlesquad.welcomeflow.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the config-facing parser, which is where a mistake is most
 * expensive: it is the layer server owners interact with, and a subtle
 * splitting bug would corrupt their flows silently.
 */
@DisplayName("ActionSpec")
class ActionSpecTest {

    @Nested
    @DisplayName("compact string form")
    class CompactForm {

        @Test
        @DisplayName("splits fields on ';;'")
        void splitsOnDoubleSemicolon() throws Exception {
            ActionSpec spec = ActionSpec.ofString("title: Hello;;World;;10;;60;;20", "test[0]");

            assertEquals("title", spec.type());
            assertEquals("Hello", spec.string("text", 0));
            assertEquals("World", spec.string("subtitle", 1));
            assertEquals(10, spec.integer("fade-in", 2, -1));
            assertEquals(60, spec.integer("stay", 3, -1));
            assertEquals(20, spec.integer("fade-out", 4, -1));
        }

        @Test
        @DisplayName("a single ';' stays inside the field -- the whole reason we use ';;'")
        void singleSemicolonIsNotASeparator() throws Exception {
            // This is the case the original spec's mixed ';;'/';' syntax got
            // wrong: MiniMessage and prose legitimately contain semicolons.
            ActionSpec spec = ActionSpec.ofString("message: Wait; then run;;extra", "test[0]");

            assertEquals("Wait; then run", spec.string("text", 0));
            assertEquals("extra", spec.string("second", 1));
        }

        @Test
        @DisplayName("'\\;;' escapes a literal ';;'")
        void escapedSeparatorIsLiteral() throws Exception {
            ActionSpec spec = ActionSpec.ofString("message: before\\;;after;;second", "test[0]");

            assertEquals("before;;after", spec.string("text", 0));
            assertEquals("second", spec.string("second", 1));
        }

        @Test
        @DisplayName("MiniMessage content with colons survives intact")
        void miniMessageWithColonsIsPreserved() throws Exception {
            // Only the FIRST colon separates type from arguments, so gradients
            // and hex colours must not be mangled.
            ActionSpec spec = ActionSpec.ofString(
                    "title: <gradient:#7ec8ff:#ffffff>Welcome</gradient>;;<gray>sub", "test[0]");

            assertEquals("title", spec.type());
            assertEquals("<gradient:#7ec8ff:#ffffff>Welcome</gradient>", spec.string("text", 0));
            assertEquals("<gray>sub", spec.string("subtitle", 1));
        }

        @Test
        @DisplayName("bracketed modifier is parsed but kept out of baseType")
        void modifierIsSeparateFromBaseType() throws Exception {
            ActionSpec spec = ActionSpec.ofString("command[console]: eco give <player> 500", "test[0]");

            assertEquals("command[console]", spec.type());
            assertEquals("command", spec.baseType());
            assertEquals("console", spec.modifier());
            assertEquals("eco give <player> 500", spec.string("command", 0));
        }

        @Test
        @DisplayName("no modifier yields null rather than empty string")
        void absentModifierIsNull() throws Exception {
            ActionSpec spec = ActionSpec.ofString("command: spawn", "test[0]");
            assertNull(spec.modifier());
            assertEquals("command", spec.baseType());
        }

        @Test
        @DisplayName("fields are trimmed")
        void fieldsAreTrimmed() throws Exception {
            ActionSpec spec = ActionSpec.ofString("sound:  ENTITY_PLAYER_LEVELUP ;; 1 ;; 1.4 ", "test[0]");
            assertEquals("ENTITY_PLAYER_LEVELUP", spec.string("sound", 0));
            assertEquals(1.4, spec.decimal("pitch", 2, 0), 0.0001);
        }

        @Test
        @DisplayName("missing colon is a named error, not a crash")
        void missingColonIsReported() {
            ActionParseException error = assertThrows(ActionParseException.class,
                    () -> ActionSpec.ofString("title Hello", "flows.welcome.on-flow-start[0]"));
            assertTrue(error.getMessage().contains("flows.welcome.on-flow-start[0]"),
                    "error must name the config path, got: " + error.getMessage());
        }

        @Test
        @DisplayName("empty entry is a named error")
        void emptyEntryIsReported() {
            assertThrows(ActionParseException.class, () -> ActionSpec.ofString("  ", "test[0]"));
        }
    }

    @Nested
    @DisplayName("expanded map form")
    class MapForm {

        @Test
        @DisplayName("reads fields by name")
        void readsByName() throws Exception {
            ActionSpec spec = ActionSpec.ofMap(Map.of(
                    "type", "title",
                    "text", "Hello",
                    "subtitle", "World",
                    "fade-in", 5), "test[0]");

            assertEquals("title", spec.type());
            assertEquals("Hello", spec.string("text", 0));
            assertEquals("World", spec.string("subtitle", 1));
            assertEquals(5, spec.integer("fade-in", 2, -1));
        }

        @Test
        @DisplayName("absent optional field falls back")
        void absentFieldFallsBack() throws Exception {
            ActionSpec spec = ActionSpec.ofMap(Map.of("type", "title", "text", "Hello"), "test[0]");
            assertEquals(99, spec.integer("stay", 3, 99));
            assertEquals("none", spec.string("subtitle", 1, "none"));
        }

        @Test
        @DisplayName("missing 'type' is a named error")
        void missingTypeIsReported() {
            ActionParseException error = assertThrows(ActionParseException.class,
                    () -> ActionSpec.ofMap(Map.of("text", "Hello"), "flows.a.on-flow-start[2]"));
            assertTrue(error.getMessage().contains("flows.a.on-flow-start[2]"));
            assertTrue(error.getMessage().contains("type"));
        }

        @Test
        @DisplayName("nested lists are readable for conditional branches")
        void nestedListsAreReadable() throws Exception {
            ActionSpec spec = ActionSpec.ofMap(Map.of(
                    "type", "conditional",
                    "condition", "permission:vip",
                    "then", List.of("message: hi")), "test[0]");

            assertEquals(1, spec.list("then").size());
            assertTrue(spec.list("else").isEmpty(), "absent list must be empty, not null");
        }
    }

    @Nested
    @DisplayName("both forms behave identically")
    class Equivalence {

        @Test
        @DisplayName("the same title reads the same either way")
        void compactAndMapAgree() throws Exception {
            ActionSpec compact = ActionSpec.ofString("title: Hello;;World;;5;;10;;15", "a");
            ActionSpec expanded = ActionSpec.ofMap(Map.of(
                    "type", "title",
                    "text", "Hello",
                    "subtitle", "World",
                    "fade-in", 5,
                    "stay", 10,
                    "fade-out", 15), "b");

            assertEquals(compact.string("text", 0), expanded.string("text", 0));
            assertEquals(compact.string("subtitle", 1), expanded.string("subtitle", 1));
            assertEquals(compact.integer("fade-in", 2, -1), expanded.integer("fade-in", 2, -1));
            assertEquals(compact.integer("stay", 3, -1), expanded.integer("stay", 3, -1));
            assertEquals(compact.integer("fade-out", 4, -1), expanded.integer("fade-out", 4, -1));
        }
    }

    @Nested
    @DisplayName("numeric field errors")
    class NumericErrors {

        @Test
        @DisplayName("a non-numeric value names the field and the value")
        void badNumberIsActionable() throws Exception {
            ActionSpec spec = ActionSpec.ofString("title: Hi;;sub;;soon", "flows.x.on-flow-start[0]");
            ActionParseException error = assertThrows(ActionParseException.class,
                    () -> spec.integer("fade-in", 2, 10));
            assertTrue(error.getMessage().contains("fade-in"));
            assertTrue(error.getMessage().contains("soon"));
        }

        @Test
        @DisplayName("requireString names the missing field")
        void requireStringIsActionable() throws Exception {
            ActionSpec spec = ActionSpec.ofString("message:", "flows.x.on-flow-start[0]");
            ActionParseException error = assertThrows(ActionParseException.class,
                    () -> spec.requireString("text", 0));
            assertTrue(error.getMessage().contains("text"));
            assertTrue(error.getMessage().contains("flows.x.on-flow-start[0]"));
        }
    }
}
