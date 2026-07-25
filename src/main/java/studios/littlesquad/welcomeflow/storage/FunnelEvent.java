package studios.littlesquad.welcomeflow.storage;

import java.util.Locale;

/**
 * The retention funnel that {@code /wf stats} renders:
 * joined -> started -> (per-step reached) -> completed -> returned.
 *
 * <p>These are aggregate counters, not per-player rows -- the whole feature
 * costs a single {@code UPDATE ... SET count = count + 1} per event, which
 * is why it earns its place in 1.0 (see the retention layer notes in
 * PLAN.md).
 */
public enum FunnelEvent {

    /** A player logged in and was considered for flow assignment. */
    JOINED,

    /** A flow was actually started for the player. */
    STARTED,

    /** The player reached the end of the flow. */
    COMPLETED,

    /** The player quit mid-flow and passed the abandon window. */
    ABANDONED,

    /** The player came back and qualified for a return bonus. */
    RETURNED,

    /** The player qualified for the second-join reward. */
    SECOND_JOIN;

    /** Stable lowercase key used as the {@code event} column value. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Per-step counter key. Kept in the same table as the flow-level events
     * so a single query fetches the whole funnel for a flow.
     *
     * @param stepId the step's configured id
     * @return e.g. {@code "step:chop:reached"}
     */
    public static String stepReachedKey(String stepId) {
        return "step:" + stepId + ":reached";
    }

    /** Per-step drop-off counter key, e.g. {@code "step:chop:abandoned"}. */
    public static String stepAbandonedKey(String stepId) {
        return "step:" + stepId + ":abandoned";
    }
}
