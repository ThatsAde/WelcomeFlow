package studios.littlesquad.welcomeflow.objective;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused on {@code matchesCommand}, the RUN_COMMAND normalisation most
 * likely to turn into support tickets. Every assertion here is a case that would
 * otherwise leave a player stuck on a tutorial step with no visible reason.
 */
@DisplayName("ObjectiveDefinition")
class ObjectiveDefinitionTest {

    private static ObjectiveDefinition commandPattern(String pattern) {
        return new ObjectiveDefinition(ObjectiveType.RUN_COMMAND, 1, null, null, pattern, null, 0);
    }

    @Nested
    @DisplayName("matchesCommand")
    class MatchesCommand {

        @Test
        @DisplayName("exact match")
        void exactMatch() {
            assertTrue(commandPattern("/kit starter").matchesCommand("/kit starter"));
        }

        @Test
        @DisplayName("case is ignored -- '/Kit Starter' must count")
        void caseInsensitive() {
            assertTrue(commandPattern("/kit starter").matchesCommand("/Kit Starter"));
            assertTrue(commandPattern("/KIT STARTER").matchesCommand("/kit starter"));
        }

        @Test
        @DisplayName("a missing or extra leading slash still matches")
        void leadingSlashOptionalOnBothSides() {
            assertTrue(commandPattern("kit starter").matchesCommand("/kit starter"));
            assertTrue(commandPattern("/kit starter").matchesCommand("kit starter"));
        }

        @Test
        @DisplayName("repeated whitespace is collapsed")
        void collapsesWhitespace() {
            assertTrue(commandPattern("/kit starter").matchesCommand("/kit    starter"));
            assertTrue(commandPattern("/kit    starter").matchesCommand("/kit starter"));
        }

        @Test
        @DisplayName("surrounding whitespace is ignored")
        void trimsInput() {
            assertTrue(commandPattern("/kit starter").matchesCommand("  /kit starter  "));
        }

        @Test
        @DisplayName("a different command does not match")
        void differentCommandDoesNotMatch() {
            assertFalse(commandPattern("/kit starter").matchesCommand("/kit vip"));
            assertFalse(commandPattern("/kit starter").matchesCommand("/spawn"));
        }

        @Test
        @DisplayName("a longer command is not an accidental match")
        void prefixDoesNotMatchWithoutWildcard() {
            assertFalse(commandPattern("/kit").matchesCommand("/kit starter"),
                    "without a wildcard, '/kit' must not be satisfied by '/kit starter'");
        }

        @Test
        @DisplayName("trailing '*' matches by prefix")
        void wildcardMatchesPrefix() {
            assertTrue(commandPattern("/kit *").matchesCommand("/kit starter"));
            assertTrue(commandPattern("/kit*").matchesCommand("/kit vip"));
            assertFalse(commandPattern("/kit *").matchesCommand("/spawn"));
        }

        @Test
        @DisplayName("null inputs are false, never an exception")
        void nullSafety() {
            assertFalse(commandPattern("/kit").matchesCommand(null));
            assertFalse(commandPattern(null).matchesCommand("/kit"));
        }
    }

    @Nested
    @DisplayName("filters")
    class Filters {

        @Test
        @DisplayName("a null material filter means 'any'")
        void nullMaterialMatchesAnything() {
            ObjectiveDefinition definition = ObjectiveDefinition.of(ObjectiveType.BREAK_BLOCK, 5);
            assertTrue(definition.matchesMaterial(org.bukkit.Material.OAK_LOG));
            assertTrue(definition.matchesMaterial(org.bukkit.Material.STONE));
        }

        @Test
        @DisplayName("a set material filter matches only itself")
        void materialFilterIsExact() {
            ObjectiveDefinition definition = new ObjectiveDefinition(
                    ObjectiveType.BREAK_BLOCK, 5, org.bukkit.Material.OAK_LOG, null, null, null, 0);
            assertTrue(definition.matchesMaterial(org.bukkit.Material.OAK_LOG));
            assertFalse(definition.matchesMaterial(org.bukkit.Material.BIRCH_LOG));
        }

        @Test
        @DisplayName("a null entity filter means 'any'")
        void nullEntityMatchesAnything() {
            ObjectiveDefinition definition = ObjectiveDefinition.of(ObjectiveType.KILL_ENTITY, 3);
            assertTrue(definition.matchesEntity(org.bukkit.entity.EntityType.ZOMBIE));
        }
    }

    @Nested
    @DisplayName("amount clamping")
    class Amounts {

        @Test
        @DisplayName("of() never produces an unsatisfiable amount below 1")
        void amountIsAtLeastOne() {
            org.junit.jupiter.api.Assertions.assertEquals(
                    1, ObjectiveDefinition.of(ObjectiveType.SEND_CHAT, 0).amount());
            org.junit.jupiter.api.Assertions.assertEquals(
                    1, ObjectiveDefinition.of(ObjectiveType.SEND_CHAT, -5).amount());
        }
    }

    @Nested
    @DisplayName("listenerless types")
    class Listenerless {

        @Test
        @DisplayName("MANUAL and PLAY_TIME need no listener; event types do")
        void onlyManualAndPlayTimeAreListenerless() {
            assertTrue(ObjectiveType.MANUAL.listenerless());
            assertTrue(ObjectiveType.PLAY_TIME.listenerless());
            assertFalse(ObjectiveType.BREAK_BLOCK.listenerless());
            assertFalse(ObjectiveType.WALK_DISTANCE.listenerless());
        }
    }
}
