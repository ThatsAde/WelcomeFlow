package studios.littlesquad.welcomeflow.flow;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.session.PlayerSession;

/**
 * Notified of flow state transitions.
 *
 * <p>Exists so {@link FlowEngine} stays the single owner of state changes
 * without also owning every consequence of them. The checklist bossbar is one
 * observer; the public Bukkit events in M4 are another. Both react to the
 * same transitions rather than the engine growing a dependency on each.
 *
 * <p>All methods default to doing nothing, so an observer implements only the
 * transitions it cares about. Called on the main thread.
 */
public interface FlowObserver {
    /** Completion actions (including delays) have finished. */
    default void onFlowActionsComplete(Player player, PlayerSession session, Flow flow) { }

    /**
     * Vetoes a flow before any state is written.
     *
     * <p>Separate from {@link #onFlowStart} because a cancellable event has
     * to be asked <em>before</em> the decision is committed. Returning false
     * from any observer stops the flow starting at all.
     *
     * @param resumed true when resuming an interrupted run
     * @return false to prevent the flow from starting
     */
    default boolean allowFlowStart(Player player, PlayerSession session, Flow flow, boolean resumed) {
        return true;
    }

    /** A flow was assigned and begun. */
    default void onFlowStart(Player player, PlayerSession session, Flow flow) {
    }

    /**
     * A step became current -- on start, on advance, and on resume after a
     * relog, which is why the observer must treat this as "render this state"
     * rather than "something new happened".
     */
    default void onStepStart(Player player, PlayerSession session, Flow flow, Step step,
                             int stepIndex, int progress) {
    }

    /** Progress was credited but the step is not finished. */
    default void onStepProgress(Player player, PlayerSession session, Flow flow, Step step,
                                int stepIndex, int progress) {
    }

    /** A step's objective was satisfied. */
    default void onStepComplete(Player player, PlayerSession session, Flow flow, Step step) {
    }

    /** The final step completed. */
    default void onFlowComplete(Player player, PlayerSession session, Flow flow) {
    }

    /** The player left mid-flow. */
    default void onFlowInterrupted(PlayerSession session, Flow flow, Step step) {
    }
}
