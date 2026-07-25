package studios.littlesquad.welcomeflow.storage;

/**
 * Lifecycle state of one player's run through one flow.
 *
 * <p>Rows are never deleted on completion: a {@code COMPLETED} row is what
 * backs the {@code previous-flow-completed} flow condition and the
 * {@code %welcomeflow_completed%} placeholder, and {@code ABANDONED} rows
 * are what make the drop-off funnel possible.
 */
public enum FlowState {

    /** Player is part-way through; this is the row that resume reads. */
    ACTIVE,

    /** Player reached the end and {@code on-flow-complete} fired. */
    COMPLETED,

    /**
     * Player quit mid-flow and did not come back within the flow's
     * {@code abandon-after-hours} window.
     */
    ABANDONED
}
