package studios.littlesquad.welcomeflow.session;

import studios.littlesquad.welcomeflow.storage.FlowProgress;
import studios.littlesquad.welcomeflow.storage.PlayerRecord;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything WelcomeFlow knows about one online player, held in memory for
 * the length of their session.
 *
 * <p>This is the authoritative copy while the player is online; storage is
 * a mirror written asynchronously on step completion, on quit and on the
 * periodic flush. Reading player state therefore never touches the disk,
 * which is what keeps the objective listeners free of I/O.
 *
 * <p>Not thread-safe by design. Mutation happens from listeners and the
 * FlowEngine, all on the main thread; the async flush only reads, and only
 * fields that are cheap snapshots. Keep it that way -- if a future feature
 * needs off-thread mutation, add explicit synchronisation rather than
 * assuming.
 */
public final class PlayerSession {

    private final UUID uuid;
    private final String playerName;
    private final PlayerRecord record;
    private final Set<String> completedFlowIds;
    private final long sessionStartedAt;

    /**
     * {@code lastSeenAt} as it stood on disk <em>before</em> this join
     * overwrote it -- i.e. when the player was last online.
     *
     * <p>Captured at load time because ReturnBonusService needs "how long
     * were they away" and the record's own {@code lastSeenAt} has already
     * been advanced to now by then. {@code 0} for a first-ever join.
     */
    private final long previousLastSeenAt;

    /**
     * Sub-unit accumulators for objectives that measure continuous values
     * (WALK_DISTANCE counts blocks travelled, PLAY_TIME counts seconds).
     * Deliberately transient: partial progress toward the next whole unit is
     * not worth a disk write, and losing under one block of walking on a
     * relog is invisible to the player.
     */
    private final Map<String, Double> transientCounters = new HashMap<>();

    private FlowProgress progress;
    private boolean dirty;
    private boolean temporary;
    private long generation;
    private long playTimeSince;

    public boolean temporary() { return temporary; }
    public void temporary(boolean value) { temporary = value; }
    public long generation() { return generation; }
    public void invalidateTasks() { generation++; }

    public void startPlayTime(long now) { playTimeSince = now; }

    /** Accounts only online time, in whole seconds, before a snapshot or pause. */
    public void capturePlayTime(long now) {
        if (playTimeSince == 0 || progress == null) return;
        int seconds = (int) Math.min(Integer.MAX_VALUE, Math.max(0, (now - playTimeSince) / 1000));
        if (seconds > 0) {
            progress.addStepProgress(seconds);
            progress.updatedAt(now);
            playTimeSince += seconds * 1000L;
            markDirty();
        }
    }

    public void pausePlayTime(long now) {
        capturePlayTime(now);
        playTimeSince = 0;
        invalidateTasks();
    }

    public PlayerSession(UUID uuid, String playerName, PlayerRecord record, FlowProgress progress,
                         Set<String> completedFlowIds, long sessionStartedAt, long previousLastSeenAt) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.record = record;
        this.progress = progress;
        this.completedFlowIds = new HashSet<>(completedFlowIds);
        this.sessionStartedAt = sessionStartedAt;
        this.previousLastSeenAt = previousLastSeenAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String playerName() {
        return playerName;
    }

    public PlayerRecord record() {
        return record;
    }

    /** The active flow run, or {@code null} when not in a flow. */
    public FlowProgress progress() {
        return progress;
    }

    public void progress(FlowProgress progress) {
        this.progress = progress;
        markDirty();
    }

    public boolean isInFlow() {
        return progress != null;
    }

    public boolean hasCompletedFlow(String flowId) {
        return completedFlowIds.contains(flowId);
    }

    public Set<String> completedFlowIds() {
        return Set.copyOf(completedFlowIds);
    }

    public void addCompletedFlow(String flowId) {
        completedFlowIds.add(flowId);
        markDirty();
    }

    /** Epoch millis when this session began (i.e. when the player joined). */
    public long sessionStartedAt() {
        return sessionStartedAt;
    }

    /** @see #previousLastSeenAt */
    public long previousLastSeenAt() {
        return previousLastSeenAt;
    }

    /**
     * Milliseconds between the player's previous logout and this join, or
     * {@code 0} on a first-ever join (nothing to have been away from).
     */
    public long timeAwayMillis() {
        return previousLastSeenAt <= 0 ? 0L : Math.max(0L, sessionStartedAt - previousLastSeenAt);
    }

    public long sessionSeconds(long now) {
        return Math.max(0L, (now - sessionStartedAt) / 1000L);
    }

    // --- transient accumulators -------------------------------------------

    /**
     * Adds to a continuous counter and returns how many <em>whole</em> units
     * have accrued, keeping the remainder for next time.
     *
     * <p>Example: three PlayerMoveEvents of 0.4 blocks each return
     * {@code 0, 0, 1} and leave {@code 0.2} banked. This is what lets
     * WALK_DISTANCE count in whole blocks without losing the fractions.
     *
     * @param key an objective-scoped counter name
     * @param delta the amount observed, ignored when not positive
     * @return whole units to credit toward the objective, possibly 0
     */
    public int addAndDrainWholeUnits(String key, double delta) {
        if (delta <= 0) {
            return 0;
        }
        double total = transientCounters.merge(key, delta, Double::sum);
        int whole = (int) Math.floor(total);
        if (whole > 0) {
            transientCounters.put(key, total - whole);
        }
        return whole;
    }

    /** Drops all continuous accumulators, e.g. when a step advances. */
    public void clearTransientCounters() {
        transientCounters.clear();
    }

    // --- dirty tracking ---------------------------------------------------

    /**
     * True when this session holds changes not yet written to storage. The
     * periodic flush uses this to skip untouched sessions, so an idle server
     * does no disk writes at all.
     */
    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
