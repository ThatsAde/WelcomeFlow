package studios.littlesquad.welcomeflow.storage;

import java.util.UUID;

/**
 * One player's position inside one flow: the row that makes
 * "resumes exactly where it stopped" work across relogs and restarts.
 *
 * <p>Only the <em>current</em> step's progress is stored rather than a
 * per-step history. Steps advance strictly in order, so
 * {@code stepIndex + stepProgress} is a complete description of where the
 * player is, and it keeps the schema to a single row per player per flow.
 *
 * <p>Timestamps are epoch milliseconds.
 */
public final class FlowProgress {

    private final UUID uuid;
    private final String flowId;
    private int stepIndex;
    private int stepProgress;
    private FlowState state;
    private long startedAt;
    private long updatedAt;

    public FlowProgress(UUID uuid, String flowId, int stepIndex, int stepProgress, FlowState state,
                        long startedAt, long updatedAt) {
        this.uuid = uuid;
        this.flowId = flowId;
        this.stepIndex = stepIndex;
        this.stepProgress = stepProgress;
        this.state = state;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
    }

    /** A fresh run of {@code flowId}, parked on step 0 with no progress. */
    public static FlowProgress start(UUID uuid, String flowId, long now) {
        return new FlowProgress(uuid, flowId, 0, 0, FlowState.ACTIVE, now, now);
    }

    public UUID uuid() {
        return uuid;
    }

    public String flowId() {
        return flowId;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public void stepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    /** Units completed toward the current step's required amount. */
    public int stepProgress() {
        return stepProgress;
    }

    public void stepProgress(int stepProgress) {
        this.stepProgress = stepProgress;
    }

    public void addStepProgress(int delta) {
        this.stepProgress += delta;
    }

    /** Moves to the next step and resets its progress counter. */
    public void advanceStep() {
        this.stepIndex++;
        this.stepProgress = 0;
    }

    public FlowState state() {
        return state;
    }

    public void state(FlowState state) {
        this.state = state;
    }

    public long startedAt() {
        return startedAt;
    }

    public void startedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void updatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
