package studios.littlesquad.welcomeflow.storage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import studios.littlesquad.welcomeflow.session.*;
import studios.littlesquad.welcomeflow.util.Scheduling;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class StorageDurabilityTest {
    @TempDir Path directory;
    SqliteStorageProvider storage;

    @BeforeEach void setup() {
        storage = new SqliteStorageProvider(directory.resolve("test.db").toFile());
        storage.initialize();
    }
    @AfterEach void close() { storage.close(); }

    @Test void failedSnapshotIsRetriedBeforeNewerSnapshot() {
        AtomicBoolean fail = new AtomicBoolean(true);
        StorageProvider flaky = (StorageProvider) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{StorageProvider.class}, (proxy, method, args) -> {
                    if (method.getName().equals("saveSnapshot") && fail.getAndSet(false))
                        throw new StorageException("simulated disk failure");
                    try { return method.invoke(storage, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        SessionManager sessions = new SessionManager(flaky, new Scheduling(null), Logger.getAnonymousLogger());
        try {
            UUID uuid = UUID.randomUUID();
            PlayerSession session = new PlayerSession(uuid, "Tester", new PlayerRecord(uuid),
                    FlowProgress.start(uuid, "welcome", 1), Set.of(), 1, 0);
            session.progress().stepProgress(3);
            session.markDirty();
            sessions.flush(session);
            session.progress().stepProgress(9);
            session.markDirty();
            sessions.flush(session);
            sessions.awaitStorage();
            assertEquals(9, storage.loadActiveProgress(uuid).stepProgress());
        } finally { sessions.flushAllBlocking(); }
    }

    @Test void clearingActivePointerKeepsCompletedHistory() {
        UUID uuid = UUID.randomUUID();
        PlayerRecord record = new PlayerRecord(uuid);
        FlowProgress complete = FlowProgress.start(uuid, "previous", 1);
        complete.state(FlowState.COMPLETED);
        storage.saveSnapshot(record, complete);
        storage.saveSnapshot(record, FlowProgress.start(uuid, "current", 2));
        storage.saveSnapshot(record, null);
        assertNull(storage.loadActiveProgress(uuid));
        assertEquals(List.of("previous"), storage.completedFlowIds(uuid));
    }

    @Test void expiryIsPerFlowAndUsesLastOnlineTime() {
        UUID shortId = UUID.randomUUID(), longId = UUID.randomUUID();
        PlayerRecord shortRecord = new PlayerRecord(shortId), longRecord = new PlayerRecord(longId);
        shortRecord.lastSeenAt(100);
        longRecord.lastSeenAt(100);
        storage.saveSnapshot(shortRecord, FlowProgress.start(shortId, "short", 1));
        storage.saveSnapshot(longRecord, FlowProgress.start(longId, "long", 1));
        assertEquals(1, storage.markStaleProgressAbandoned("short", 200));
        assertEquals(0, storage.markStaleProgressAbandoned("long", 50));
        assertNull(storage.loadActiveProgress(shortId));
        assertNotNull(storage.loadActiveProgress(longId));
        assertEquals(1L, storage.funnelCounts("short").get("abandoned"));
    }
}
