package studios.littlesquad.welcomeflow.session;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import studios.littlesquad.welcomeflow.storage.FlowProgress;
import studios.littlesquad.welcomeflow.storage.PlayerRecord;
import studios.littlesquad.welcomeflow.storage.StorageException;
import studios.littlesquad.welcomeflow.storage.StorageProvider;
import studios.littlesquad.welcomeflow.util.Scheduling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the lifecycle of {@link PlayerSession}s: loads one asynchronously on
 * join, keeps it in memory while the player is online, and flushes it
 * asynchronously on step completion, on quit, and on a periodic timer.
 *
 * <p>The periodic flush is the only scheduled task in the plugin and it is
 * cheap on purpose: it iterates the online sessions and writes just the
 * dirty ones, so an idle server performs zero disk writes. Everything else
 * here is event-driven, per the "no per-tick scanning loops" constraint.
 */
public final class SessionManager {

    private final StorageProvider storage;
    private final Scheduling scheduling;
    private final Logger logger;

    /** Concurrent because the async flush reads it while the main thread mutates. */
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    private BukkitTask flushTask;

    public SessionManager(StorageProvider storage, Scheduling scheduling, Logger logger) {
        this.storage = storage;
        this.scheduling = scheduling;
        this.logger = logger;
    }

    /**
     * Loads (off-thread) and registers the session for a joining player,
     * then hands it to {@code onLoaded} on the main thread.
     *
     * <p>Bumps and persists the join counters as part of the same async
     * pass, so a crash right after login still records the join. The
     * player's previous {@code lastSeenAt} is captured into the session
     * before being overwritten -- ReturnBonusService needs it.
     *
     * @param onLoaded receives the session on the main thread, or is not
     *     called at all if loading failed (the failure is logged)
     */
    public void loadOnJoin(Player player, Consumer<PlayerSession> onLoaded) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        long now = System.currentTimeMillis();

        scheduling.supplyAsync(() -> {
            try {
                PlayerRecord record = storage.loadPlayer(uuid);
                long previousLastSeenAt = record.lastSeenAt();

                record.incrementJoinCount();
                if (record.firstJoinAt() == 0L) {
                    record.firstJoinAt(now);
                }
                record.lastSeenAt(now);
                storage.savePlayer(record);

                FlowProgress progress = storage.loadActiveProgress(uuid);
                Set<String> completed = new HashSet<>(storage.completedFlowIds(uuid));

                return new PlayerSession(uuid, name, record, progress, completed, now, previousLastSeenAt);
            } catch (StorageException e) {
                logger.log(Level.SEVERE, "Could not load WelcomeFlow data for " + name
                        + "; they will not be assigned a flow this session.", e);
                return null;
            }
        }, session -> {
            if (session == null) {
                return;
            }
            // The player may have left during the async load.
            if (!player.isOnline()) {
                flush(session);
                return;
            }
            sessions.put(uuid, session);
            onLoaded.accept(session);
        });
    }

    /**
     * Removes the player's session and flushes it, recording how long the
     * session lasted (and, on a first session, banking that duration as the
     * second-join anti-farm guard).
     *
     * @return the removed session, or {@code null} if there was none
     */
    public PlayerSession unloadOnQuit(UUID uuid) {
        PlayerSession session = sessions.remove(uuid);
        if (session == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        PlayerRecord record = session.record();
        record.lastSeenAt(now);

        // Bank the first session's length once, and only once: this is what
        // stops the second-join reward being farmed with instant relogs.
        if (record.joinCount() <= 1 && record.firstSessionSeconds() == 0L) {
            record.firstSessionSeconds(session.sessionSeconds(now));
        }
        session.markDirty();
        flush(session);
        return session;
    }

    public PlayerSession get(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean isLoaded(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /** Live view of the loaded sessions; safe to iterate. */
    public Iterable<PlayerSession> loadedSessions() {
        return sessions.values();
    }

    public int loadedCount() {
        return sessions.size();
    }

    /** Drops the in-memory session without writing it ({@code /wf reset}). */
    public void discard(UUID uuid) {
        sessions.remove(uuid);
    }

    // --- flushing ---------------------------------------------------------

    /** Writes one session off-thread if it has pending changes. */
    public void flush(PlayerSession session) {
        if (!session.isDirty()) {
            return;
        }
        // Snapshot the mutable bits on the calling (main) thread, then write
        // off it -- handing PlayerSession itself to another thread would race
        // with the listeners still mutating it.
        PlayerRecord recordSnapshot = copyOf(session.record());
        FlowProgress progressSnapshot = copyOf(session.progress());
        session.clearDirty();

        scheduling.runAsync(() -> writeSnapshot(session.playerName(), recordSnapshot, progressSnapshot));
    }

    /**
     * Starts the periodic safety-net flush.
     *
     * @param intervalSeconds from config; values below 1 disable the task
     */
    public void startPeriodicFlush(int intervalSeconds) {
        stopPeriodicFlush();
        if (intervalSeconds < 1) {
            logger.info("Periodic session flush is disabled (interval < 1s).");
            return;
        }
        long periodTicks = intervalSeconds * 20L;
        // Runs async: it only touches snapshots and storage, never Bukkit.
        flushTask = scheduling.runAsyncTimer(this::flushDirtySessions, periodTicks, periodTicks);
    }

    public void stopPeriodicFlush() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    /**
     * Writes every dirty session. Already off the main thread when called
     * from the timer.
     *
     * <p>Snapshotting from a non-main thread is a deliberate, bounded risk:
     * the worst case is a torn read that persists a step counter one event
     * stale, which the next flush corrects. The alternative -- bouncing to
     * the main thread for every player every interval -- costs more than the
     * inconsistency it would prevent.
     */
    private void flushDirtySessions() {
        for (PlayerSession session : sessions.values()) {
            if (!session.isDirty()) {
                continue;
            }
            PlayerRecord recordSnapshot = copyOf(session.record());
            FlowProgress progressSnapshot = copyOf(session.progress());
            session.clearDirty();
            writeSnapshot(session.playerName(), recordSnapshot, progressSnapshot);
        }
    }

    /**
     * Flushes everything synchronously. Called from {@code onDisable}, where
     * the scheduler is already shutting down and an async task would simply
     * never run.
     */
    public void flushAllBlocking() {
        List<PlayerSession> pending = new ArrayList<>(sessions.values());
        long now = System.currentTimeMillis();
        for (PlayerSession session : pending) {
            session.record().lastSeenAt(now);
            writeSnapshot(session.playerName(), copyOf(session.record()), copyOf(session.progress()));
            session.clearDirty();
        }
        sessions.clear();
    }

    private void writeSnapshot(String playerName, PlayerRecord record, FlowProgress progress) {
        try {
            storage.savePlayer(record);
            if (progress != null) {
                storage.saveProgress(progress);
            }
        } catch (StorageException e) {
            logger.log(Level.SEVERE, "Could not save WelcomeFlow data for " + playerName, e);
        }
    }

    private static PlayerRecord copyOf(PlayerRecord source) {
        return new PlayerRecord(
                source.uuid(),
                source.joinCount(),
                source.firstJoinAt(),
                source.lastSeenAt(),
                source.firstSessionSeconds(),
                source.secondJoinClaimed(),
                source.returnBonusClaims(),
                source.lastReturnBonusAt());
    }

    private static FlowProgress copyOf(FlowProgress source) {
        return source == null ? null : new FlowProgress(
                source.uuid(),
                source.flowId(),
                source.stepIndex(),
                source.stepProgress(),
                source.state(),
                source.startedAt(),
                source.updatedAt());
    }
}
