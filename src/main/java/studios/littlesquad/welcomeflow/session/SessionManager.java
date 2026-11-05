package studios.littlesquad.welcomeflow.session;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import studios.littlesquad.welcomeflow.storage.*;
import studios.littlesquad.welcomeflow.util.Scheduling;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main-thread session ownership with ordered, off-thread storage operations. */
public final class SessionManager {
    private final StorageProvider storage;
    private final Scheduling scheduling;
    private final Logger logger;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final Map<UUID, PlayerSession> previews = new HashMap<>();
    private final Map<UUID, Object> loads = new HashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WelcomeFlow-storage");
        thread.setDaemon(true);
        return thread;
    });
    // Worker-owned retry queue: newer operations never overtake a failed write.
    private final Deque<Runnable> writes = new ArrayDeque<>();
    private BukkitTask flushTask;
    private volatile boolean closing;

    public SessionManager(StorageProvider storage, Scheduling scheduling, Logger logger) {
        this.storage = storage;
        this.scheduling = scheduling;
        this.logger = logger;
    }

    public void loadOnJoin(Player player, Consumer<PlayerSession> onLoaded) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        long now = System.currentTimeMillis();
        Object token = new Object();
        loads.put(uuid, token);
        io.execute(() -> {
            try {
                drainWrites();
                PlayerRecord record = storage.loadPlayer(uuid);
                long previousSeen = record.lastSeenAt();
                record.incrementJoinCount();
                if (record.firstJoinAt() == 0) record.firstJoinAt(now);
                record.lastSeenAt(now);
                storage.savePlayer(record);
                PlayerSession session = new PlayerSession(uuid, name, record,
                        storage.loadActiveProgress(uuid), new HashSet<>(storage.completedFlowIds(uuid)),
                        now, previousSeen);
                if (!closing) scheduling.runSync(() -> {
                    if (loads.get(uuid) != token || !player.isOnline()) return;
                    loads.remove(uuid);
                    sessions.put(uuid, session);
                    onLoaded.accept(session);
                });
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not load WelcomeFlow data for " + name, e);
            }
        });
    }

    public PlayerSession unloadOnQuit(UUID uuid) {
        loads.remove(uuid);
        endPreview(uuid);
        PlayerSession session = sessions.remove(uuid);
        if (session != null) {
            finishSession(session);
            flush(session);
        }
        return session;
    }

    private void finishSession(PlayerSession session) {
        long now = System.currentTimeMillis();
        session.pausePlayTime(now);
        session.record().lastSeenAt(now);
        if (session.record().joinCount() <= 1 && session.record().firstSessionSeconds() == 0) {
            session.record().firstSessionSeconds(session.sessionSeconds(now));
        }
        session.markDirty();
    }

    public PlayerSession get(UUID uuid) { return sessions.get(uuid); }
    public boolean isLoaded(UUID uuid) { return sessions.containsKey(uuid); }
    public Iterable<PlayerSession> loadedSessions() { return List.copyOf(sessions.values()); }
    public int loadedCount() { return sessions.size(); }

    public void discard(UUID uuid) {
        loads.remove(uuid);
        PlayerSession session = sessions.remove(uuid);
        if (session != null) session.pausePlayTime(System.currentTimeMillis());
        PlayerSession original = previews.remove(uuid);
        if (original != null) original.invalidateTasks();
    }

    /** Parks real progress; preview writes and funnel events are suppressed. */
    public PlayerSession beginPreview(UUID uuid) {
        endPreview(uuid);
        PlayerSession original = Objects.requireNonNull(sessions.get(uuid), "Session not loaded");
        original.pausePlayTime(System.currentTimeMillis());
        flush(original);
        previews.put(uuid, original);
        PlayerSession preview = new PlayerSession(uuid, original.playerName(), copyOf(original.record()),
                null, original.completedFlowIds(), original.sessionStartedAt(), original.previousLastSeenAt());
        preview.temporary(true);
        sessions.put(uuid, preview);
        return preview;
    }

    public boolean isPreview(UUID uuid) { return previews.containsKey(uuid); }

    public PlayerSession endPreview(UUID uuid) {
        PlayerSession original = previews.remove(uuid);
        if (original == null) return null;
        PlayerSession preview = sessions.put(uuid, original);
        if (preview != null) preview.pausePlayTime(System.currentTimeMillis());
        return original;
    }

    /** Resets after all older writes; completion runs on the main thread. */
    public void reset(UUID uuid, Runnable onReset) {
        discard(uuid);
        enqueue(() -> {
            storage.resetPlayer(uuid);
            if (!closing) scheduling.runSync(onReset);
        });
    }

    public void flush(PlayerSession session) {
        if (session.temporary()) return;
        session.capturePlayTime(System.currentTimeMillis());
        if (!session.isDirty()) return;
        PlayerRecord record = copyOf(session.record());
        FlowProgress progress = copyOf(session.progress());
        enqueue(() -> storage.saveSnapshot(record, progress));
        session.clearDirty(); // The immutable snapshot is owned by the retry queue.
    }

    private void enqueue(Runnable write) {
        io.execute(() -> {
            writes.addLast(write);
            retryWrites();
        });
    }

    private void drainWrites() {
        while (!writes.isEmpty()) {
            writes.getFirst().run();
            writes.removeFirst();
        }
    }

    private void retryWrites() {
        try { drainWrites(); }
        catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Storage write failed; queued for retry before subsequent writes.", e);
        }
    }

    public void startPeriodicFlush(int intervalSeconds) {
        stopPeriodicFlush();
        // Retry failed writes even when periodic snapshots are disabled.
        long ticks = (intervalSeconds > 0 ? intervalSeconds : 30) * 20L;
        flushTask = scheduling.runSyncTimer(() -> {
            if (intervalSeconds > 0) sessions.values().forEach(this::flush);
            io.execute(this::retryWrites);
        }, ticks, ticks);
    }

    public void stopPeriodicFlush() {
        if (flushTask != null) { flushTask.cancel(); flushTask = null; }
    }

    /** Barrier for shutdown and tests; no Bukkit calls run on the worker. */
    public void awaitStorage() {
        try { io.submit(this::drainWrites).get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        catch (ExecutionException e) { throw new IllegalStateException("Storage queue did not drain", e.getCause()); }
    }

    public void flushAllBlocking() {
        stopPeriodicFlush();
        closing = true;
        new ArrayList<>(previews.keySet()).forEach(this::endPreview);
        sessions.values().forEach(session -> { finishSession(session); flush(session); });
        try { awaitStorage(); }
        catch (RuntimeException e) { logger.log(Level.SEVERE, "Unable to save all data during shutdown", e); }
        finally { io.shutdown(); sessions.clear(); loads.clear(); }
    }

    private static PlayerRecord copyOf(PlayerRecord s) {
        return new PlayerRecord(s.uuid(), s.joinCount(), s.firstJoinAt(), s.lastSeenAt(),
                s.firstSessionSeconds(), s.secondJoinClaimed(), s.returnBonusClaims(), s.lastReturnBonusAt());
    }

    private static FlowProgress copyOf(FlowProgress s) {
        return s == null ? null : new FlowProgress(s.uuid(), s.flowId(), s.stepIndex(), s.stepProgress(),
                s.state(), s.startedAt(), s.updatedAt());
    }
}
