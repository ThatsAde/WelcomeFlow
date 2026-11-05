package studios.littlesquad.welcomeflow.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real SQLite provider against a temp-file database. No
 * Bukkit involved, so this needs neither MockBukkit nor a server, which
 * makes it the fastest and most trustworthy layer of the test suite.
 */
@DisplayName("SqliteStorageProvider")
class SqliteStorageProviderTest {

    @TempDir
    Path tempDir;

    private SqliteStorageProvider storage;
    private File databaseFile;

    @BeforeEach
    void setUp() {
        databaseFile = tempDir.resolve("data.db").toFile();
        storage = new SqliteStorageProvider(databaseFile);
        storage.initialize();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    @DisplayName("initialize() creates the database file")
    void initializeCreatesFile() {
        assertTrue(databaseFile.exists(), "database file was not created");
    }

    @Test
    @DisplayName("initialize() is idempotent on an existing database")
    void initializeIsIdempotent() {
        UUID uuid = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(uuid);
        record.joinCount(7);
        storage.savePlayer(record);
        storage.close();

        // Re-opening must migrate without wiping anything.
        SqliteStorageProvider reopened = new SqliteStorageProvider(databaseFile);
        reopened.initialize();
        try {
            assertEquals(7, reopened.loadPlayer(uuid).joinCount(), "data lost across reopen");
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("using storage after close() fails loudly, not silently")
    void useAfterCloseThrows() {
        storage.close();
        assertThrows(StorageException.class, () -> storage.loadPlayer(UUID.randomUUID()));
    }

    @Nested
    @DisplayName("players")
    class Players {

        @Test
        @DisplayName("unknown player returns a zeroed record, never null")
        void unknownPlayerReturnsBlankRecord() {
            PlayerRecord record = storage.loadPlayer(UUID.randomUUID());
            assertNotNull(record, "loadPlayer must never return null");
            assertEquals(0, record.joinCount());
            assertFalse(record.secondJoinClaimed());
        }

        @Test
        @DisplayName("every field survives a save/load round trip")
        void roundTripPreservesEveryField() {
            UUID uuid = UUID.randomUUID();
            PlayerRecord original = new PlayerRecord(uuid, 4, 1_000L, 2_000L, 300L, true, 2, 3_000L);
            storage.savePlayer(original);

            PlayerRecord loaded = storage.loadPlayer(uuid);
            assertEquals(4, loaded.joinCount());
            assertEquals(1_000L, loaded.firstJoinAt());
            assertEquals(2_000L, loaded.lastSeenAt());
            assertEquals(300L, loaded.firstSessionSeconds());
            assertTrue(loaded.secondJoinClaimed());
            assertEquals(2, loaded.returnBonusClaims());
            assertEquals(3_000L, loaded.lastReturnBonusAt());
        }

        @Test
        @DisplayName("saving twice updates rather than duplicating")
        void saveIsUpsert() {
            UUID uuid = UUID.randomUUID();
            PlayerRecord record = new PlayerRecord(uuid);
            record.joinCount(1);
            storage.savePlayer(record);

            record.joinCount(2);
            record.secondJoinClaimed(true);
            storage.savePlayer(record);

            PlayerRecord loaded = storage.loadPlayer(uuid);
            assertEquals(2, loaded.joinCount(), "second save did not update the row");
            assertTrue(loaded.secondJoinClaimed());
        }
    }

    @Nested
    @DisplayName("flow progress")
    class Progress {

        @Test
        @DisplayName("no active progress for an unknown player")
        void noActiveProgressInitially() {
            assertNull(storage.loadActiveProgress(UUID.randomUUID()));
        }

        @Test
        @DisplayName("active progress round-trips exactly: the resume guarantee")
        void activeProgressRoundTrips() {
            UUID uuid = UUID.randomUUID();
            FlowProgress progress = new FlowProgress(uuid, "welcome", 3, 2, FlowState.ACTIVE, 100L, 200L);
            storage.saveProgress(progress);

            FlowProgress loaded = storage.loadActiveProgress(uuid);
            assertNotNull(loaded);
            assertEquals("welcome", loaded.flowId());
            assertEquals(3, loaded.stepIndex(), "resume would restart the player on the wrong step");
            assertEquals(2, loaded.stepProgress(), "resume would lose partial step progress");
            assertEquals(FlowState.ACTIVE, loaded.state());
            assertEquals(100L, loaded.startedAt());
        }

        @Test
        @DisplayName("completed progress is not returned as active")
        void completedProgressIsNotActive() {
            UUID uuid = UUID.randomUUID();
            FlowProgress progress = FlowProgress.start(uuid, "welcome", 100L);
            progress.state(FlowState.COMPLETED);
            storage.saveProgress(progress);

            assertNull(storage.loadActiveProgress(uuid), "a finished flow must not be resumed");
            assertEquals(List.of("welcome"), storage.completedFlowIds(uuid));
        }

        @Test
        @DisplayName("completedFlowIds excludes active and abandoned runs")
        void completedFlowIdsOnlyCountsCompleted() {
            UUID uuid = UUID.randomUUID();

            FlowProgress done = FlowProgress.start(uuid, "done-flow", 1L);
            done.state(FlowState.COMPLETED);
            storage.saveProgress(done);

            storage.saveProgress(FlowProgress.start(uuid, "active-flow", 2L));

            FlowProgress dropped = FlowProgress.start(uuid, "dropped-flow", 3L);
            dropped.state(FlowState.ABANDONED);
            storage.saveProgress(dropped);

            assertEquals(List.of("done-flow"), storage.completedFlowIds(uuid));
        }

        @Test
        @DisplayName("re-saving the same flow updates in place")
        void progressSaveIsUpsertPerFlow() {
            UUID uuid = UUID.randomUUID();
            FlowProgress progress = FlowProgress.start(uuid, "welcome", 100L);
            storage.saveProgress(progress);

            progress.advanceStep();
            progress.addStepProgress(5);
            progress.updatedAt(400L);
            storage.saveProgress(progress);

            FlowProgress loaded = storage.loadActiveProgress(uuid);
            assertNotNull(loaded);
            assertEquals(1, loaded.stepIndex());
            assertEquals(5, loaded.stepProgress());
        }
    }

    @Nested
    @DisplayName("stale abandon sweep")
    class StaleSweep {

        @Test
        @DisplayName("only rows older than the cutoff are reclassified")
        void marksOnlyStaleRows() {
            UUID stale = UUID.randomUUID();
            UUID fresh = UUID.randomUUID();
            storage.saveProgress(new FlowProgress(stale, "welcome", 1, 0, FlowState.ACTIVE, 0L, 1_000L));
            storage.saveProgress(new FlowProgress(fresh, "welcome", 1, 0, FlowState.ACTIVE, 0L, 9_000L));

            int changed = storage.markStaleProgressAbandoned(5_000L);

            assertEquals(1, changed, "wrong number of rows abandoned");
            assertNull(storage.loadActiveProgress(stale), "stale row should no longer be active");
            assertNotNull(storage.loadActiveProgress(fresh), "fresh row must be left alone");
        }

        @Test
        @DisplayName("sweeping credits the abandoned funnel counter")
        void sweepFeedsTheFunnel() {
            storage.saveProgress(new FlowProgress(UUID.randomUUID(), "welcome", 1, 0, FlowState.ACTIVE, 0L, 10L));
            storage.saveProgress(new FlowProgress(UUID.randomUUID(), "welcome", 2, 0, FlowState.ACTIVE, 0L, 20L));

            storage.markStaleProgressAbandoned(1_000L);

            assertEquals(2L, storage.funnelCounts("welcome").get(FunnelEvent.ABANDONED.key()));
        }

        @Test
        @DisplayName("sweeping an empty table is a no-op")
        void emptySweepIsHarmless() {
            assertEquals(0, storage.markStaleProgressAbandoned(System.currentTimeMillis()));
        }
    }

    @Nested
    @DisplayName("funnel counters")
    class Funnel {

        @Test
        @DisplayName("counts start empty and accumulate")
        void countersAccumulate() {
            assertTrue(storage.funnelCounts("welcome").isEmpty());

            storage.incrementFunnel("welcome", FunnelEvent.JOINED.key());
            storage.incrementFunnel("welcome", FunnelEvent.JOINED.key());
            storage.incrementFunnel("welcome", FunnelEvent.STARTED.key());

            Map<String, Long> counts = storage.funnelCounts("welcome");
            assertEquals(2L, counts.get(FunnelEvent.JOINED.key()));
            assertEquals(1L, counts.get(FunnelEvent.STARTED.key()));
        }

        @Test
        @DisplayName("counters are scoped per flow")
        void countersAreScopedPerFlow() {
            storage.incrementFunnel("welcome", FunnelEvent.JOINED.key());
            storage.incrementFunnel("skyblock", FunnelEvent.JOINED.key());
            storage.incrementFunnel("skyblock", FunnelEvent.JOINED.key());

            assertEquals(1L, storage.funnelCounts("welcome").get(FunnelEvent.JOINED.key()));
            assertEquals(2L, storage.funnelCounts("skyblock").get(FunnelEvent.JOINED.key()));
        }

        @Test
        @DisplayName("per-step drop-off keys are tracked alongside flow events")
        void perStepKeysCoexist() {
            storage.incrementFunnel("welcome", FunnelEvent.stepReachedKey("chop"));
            storage.incrementFunnel("welcome", FunnelEvent.stepAbandonedKey("chop"));
            storage.incrementFunnel("welcome", FunnelEvent.STARTED.key());

            Map<String, Long> counts = storage.funnelCounts("welcome");
            assertEquals(1L, counts.get("step:chop:reached"));
            assertEquals(1L, counts.get("step:chop:abandoned"));
            assertEquals(1L, counts.get(FunnelEvent.STARTED.key()));
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("reset clears progress and player flags together")
        void resetClearsEverythingForThatPlayer() {
            UUID uuid = UUID.randomUUID();
            PlayerRecord record = new PlayerRecord(uuid);
            record.joinCount(5);
            record.secondJoinClaimed(true);
            storage.savePlayer(record);
            storage.saveProgress(FlowProgress.start(uuid, "welcome", 100L));

            storage.resetPlayer(uuid);

            assertNull(storage.loadActiveProgress(uuid));
            PlayerRecord reloaded = storage.loadPlayer(uuid);
            assertEquals(0, reloaded.joinCount(), "reward flags must be wiped, not just progress");
            assertFalse(reloaded.secondJoinClaimed());
        }

        @Test
        @DisplayName("reset leaves other players untouched")
        void resetIsScopedToOnePlayer() {
            UUID target = UUID.randomUUID();
            UUID bystander = UUID.randomUUID();
            PlayerRecord other = new PlayerRecord(bystander);
            other.joinCount(3);
            storage.savePlayer(other);
            storage.saveProgress(FlowProgress.start(bystander, "welcome", 100L));
            storage.savePlayer(new PlayerRecord(target));

            storage.resetPlayer(target);

            assertEquals(3, storage.loadPlayer(bystander).joinCount());
            assertNotNull(storage.loadActiveProgress(bystander));
        }
    }
}
