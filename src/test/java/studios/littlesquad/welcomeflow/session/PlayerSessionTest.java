package studios.littlesquad.welcomeflow.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import studios.littlesquad.welcomeflow.storage.FlowProgress;
import studios.littlesquad.welcomeflow.storage.PlayerRecord;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PlayerSession")
class PlayerSessionTest {

    private static PlayerSession session(long startedAt, long previousLastSeenAt) {
        UUID uuid = UUID.randomUUID();
        return new PlayerSession(uuid, "Tester", new PlayerRecord(uuid), null, Set.of(),
                startedAt, previousLastSeenAt);
    }

    @Nested
    @DisplayName("fractional accumulator")
    class Accumulator {

        @Test
        @DisplayName("banks fractions and yields whole units, as WALK_DISTANCE counts")
        void banksFractions() {
            PlayerSession session = session(0L, 0L);

            // Three 0.4-block moves: no credit, no credit, then one block.
            assertEquals(0, session.addAndDrainWholeUnits("walk", 0.4));
            assertEquals(0, session.addAndDrainWholeUnits("walk", 0.4));
            assertEquals(1, session.addAndDrainWholeUnits("walk", 0.4));
            // 0.2 remains banked, so two more 0.4s produce the next block.
            assertEquals(0, session.addAndDrainWholeUnits("walk", 0.4));
            assertEquals(1, session.addAndDrainWholeUnits("walk", 0.4));
        }

        @Test
        @DisplayName("a large jump credits every whole unit at once")
        void creditsMultipleUnits() {
            PlayerSession session = session(0L, 0L);
            assertEquals(7, session.addAndDrainWholeUnits("walk", 7.9));
            // The 0.9 remainder must survive.
            assertEquals(1, session.addAndDrainWholeUnits("walk", 0.2));
        }

        @Test
        @DisplayName("counters are independent per key")
        void keysAreIndependent() {
            PlayerSession session = session(0L, 0L);
            session.addAndDrainWholeUnits("walk", 0.6);
            assertEquals(0, session.addAndDrainWholeUnits("play", 0.6),
                    "a different counter must not inherit banked progress");
        }

        @Test
        @DisplayName("zero and negative deltas are ignored")
        void ignoresNonPositive() {
            PlayerSession session = session(0L, 0L);
            assertEquals(0, session.addAndDrainWholeUnits("walk", 0));
            assertEquals(0, session.addAndDrainWholeUnits("walk", -5));
        }

        @Test
        @DisplayName("clearing drops banked progress, as on step advance")
        void clearResetsBank() {
            PlayerSession session = session(0L, 0L);
            session.addAndDrainWholeUnits("walk", 0.9);
            session.clearTransientCounters();
            assertEquals(0, session.addAndDrainWholeUnits("walk", 0.5),
                    "banked fractions must not carry across a step boundary");
        }
    }

    @Nested
    @DisplayName("time away")
    class TimeAway {

        @Test
        @DisplayName("computed from the previous logout, not the current record")
        void computesFromPreviousLastSeen() {
            long previousLogout = 1_000_000L;
            long joinedAt = previousLogout + 3_600_000L;
            assertEquals(3_600_000L, session(joinedAt, previousLogout).timeAwayMillis());
        }

        @Test
        @DisplayName("a first-ever join reports zero, not a huge span")
        void firstJoinHasNoTimeAway() {
            assertEquals(0L, session(System.currentTimeMillis(), 0L).timeAwayMillis());
        }

        @Test
        @DisplayName("clock skew cannot produce a negative span")
        void neverNegative() {
            assertEquals(0L, session(1_000L, 5_000L).timeAwayMillis());
        }
    }

    @Nested
    @DisplayName("dirty tracking")
    class Dirty {

        @Test
        @DisplayName("a fresh session is clean, so idle servers do no writes")
        void freshSessionIsClean() {
            assertFalse(session(0L, 0L).isDirty());
        }

        @Test
        @DisplayName("changing progress marks it dirty")
        void progressChangeMarksDirty() {
            PlayerSession session = session(0L, 0L);
            session.progress(FlowProgress.start(session.uuid(), "welcome", 0L));
            assertTrue(session.isDirty());
        }

        @Test
        @DisplayName("clearing resets the flag")
        void clearResetsFlag() {
            PlayerSession session = session(0L, 0L);
            session.markDirty();
            session.clearDirty();
            assertFalse(session.isDirty());
        }
    }

    @Nested
    @DisplayName("completed flows")
    class Completed {

        @Test
        @DisplayName("tracks completion for the previous-flow-completed condition")
        void tracksCompletion() {
            PlayerSession session = session(0L, 0L);
            assertFalse(session.hasCompletedFlow("welcome"));
            session.addCompletedFlow("welcome");
            assertTrue(session.hasCompletedFlow("welcome"));
            assertTrue(session.isDirty());
        }
    }

    @Nested
    @DisplayName("session length")
    class SessionLength {

        @Test
        @DisplayName("reported in whole seconds and never negative")
        void sessionSeconds() {
            PlayerSession session = session(10_000L, 0L);
            assertEquals(5L, session.sessionSeconds(15_000L));
            assertEquals(0L, session.sessionSeconds(9_000L));
        }
    }
}
