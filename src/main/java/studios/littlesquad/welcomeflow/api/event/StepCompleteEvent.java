package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player satisfies a step's objective, before the flow advances
 * and before the step's {@code on-step-complete} actions run.
 *
 * <p>Not cancellable. The objective has genuinely been met (the player
 * broke the blocks, ran the command), so refusing to advance would leave
 * them stuck on a step they already finished, with no way to progress. To
 * gate progression, express the condition in the flow's own configuration
 * instead.
 */
public class StepCompleteEvent extends WelcomeFlowEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String stepId;
    private final int stepIndex;
    private final int stepCount;
    private final boolean forced;

    /**
     * @param forced true when completed by {@code /wf skip} or the API rather
     *     than by the player meeting the objective
     */
    public StepCompleteEvent(Player player, String flowId, String stepId, int stepIndex, int stepCount,
                             boolean forced) {
        super(player, flowId);
        this.stepId = stepId;
        this.stepIndex = stepIndex;
        this.stepCount = stepCount;
        this.forced = forced;
    }

    public String getStepId() {
        return stepId;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public int getStepCount() {
        return stepCount;
    }

    /** True when an admin or the API completed this step, not the player. */
    public boolean isForced() {
        return forced;
    }

    /** True when this was the flow's last step. */
    public boolean isFinalStep() {
        return stepIndex >= stepCount - 1;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
