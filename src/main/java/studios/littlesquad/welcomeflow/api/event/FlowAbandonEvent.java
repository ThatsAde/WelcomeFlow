package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player quits part-way through a flow.
 *
 * <p><strong>The player is offline when this fires</strong>, which is why it
 * extends {@link Event} directly rather than {@link WelcomeFlowEvent}: there
 * is no online {@code Player} to expose. Do not attempt to message or teleport
 * them here.
 *
 * <p>Note this fires on <em>every</em> mid-flow quit, not only on permanent
 * abandonment: the player can still resume next login. The run is only
 * reclassified as truly abandoned once the flow's {@code abandon-after-hours}
 * window has elapsed, which is swept at startup.
 *
 * <p>Useful for feeding your own analytics: {@link #getStepId()} is the step
 * they dropped on.
 */
public class FlowAbandonEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final String flowId;
    private final String stepId;
    private final int stepIndex;
    private final int stepCount;

    public FlowAbandonEvent(OfflinePlayer player, String flowId, String stepId, int stepIndex, int stepCount) {
        this.player = player;
        this.flowId = flowId;
        this.stepId = stepId;
        this.stepIndex = stepIndex;
        this.stepCount = stepCount;
    }

    /** The player, who is offline by the time this fires. */
    public OfflinePlayer getPlayer() {
        return player;
    }

    public String getFlowId() {
        return flowId;
    }

    /** The step they dropped on: the drop-off point for your funnel. */
    public String getStepId() {
        return stepId;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public int getStepCount() {
        return stepCount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
