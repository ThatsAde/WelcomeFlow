package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player finishes the last step of a flow, before the flow's
 * {@code on-flow-complete} actions run.
 *
 * <p>The completion has already been persisted when this fires, so the flow
 * will not be offered again and
 * {@code WelcomeFlowAPI.hasCompletedFlow(uuid, flowId)} already returns true.
 * This is the hook for granting your own completion rewards in code rather
 * than config.
 */
public class FlowCompleteEvent extends WelcomeFlowEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int stepCount;
    private final long startedAt;
    private final long completedAt;

    public FlowCompleteEvent(Player player, String flowId, int stepCount, long startedAt, long completedAt) {
        super(player, flowId);
        this.stepCount = stepCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public int getStepCount() {
        return stepCount;
    }

    /** Epoch millis when the player started this flow. */
    public long getStartedAt() {
        return startedAt;
    }

    /** Epoch millis when they finished it. */
    public long getCompletedAt() {
        return completedAt;
    }

    /**
     * Wall-clock milliseconds from start to finish.
     *
     * <p>Includes time spent offline if the player took several sessions,
     * which is usually what you want for "how long did onboarding take" but
     * is not the same as time played.
     */
    public long getDurationMillis() {
        return Math.max(0L, completedAt - startedAt);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
