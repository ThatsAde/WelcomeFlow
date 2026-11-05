package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a step becomes the player's current one.
 *
 * <p>Also fires on resume after a relog, so treat it as "the player is now on
 * this step" rather than "the player just advanced". Use
 * {@link StepCompleteEvent} to detect actual progress.
 *
 * <p>Not cancellable: by the time a step starts, the previous one has already
 * completed and been persisted, so there is nothing coherent to cancel.
 */
public class StepStartEvent extends WelcomeFlowEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String stepId;
    private final int stepIndex;
    private final int stepCount;
    private final int progress;
    private final int required;

    public StepStartEvent(Player player, String flowId, String stepId, int stepIndex, int stepCount,
                          int progress, int required) {
        super(player, flowId);
        this.stepId = stepId;
        this.stepIndex = stepIndex;
        this.stepCount = stepCount;
        this.progress = progress;
        this.required = required;
    }

    /** The step's id as written in flows.yml. */
    public String getStepId() {
        return stepId;
    }

    /** Zero-based position in the flow. */
    public int getStepIndex() {
        return stepIndex;
    }

    public int getStepCount() {
        return stepCount;
    }

    /** Units already credited; non-zero when resuming a part-done step. */
    public int getProgress() {
        return progress;
    }

    /** Units needed to complete this step. */
    public int getRequired() {
        return required;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
