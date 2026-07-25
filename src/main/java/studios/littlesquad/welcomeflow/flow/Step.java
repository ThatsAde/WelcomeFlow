package studios.littlesquad.welcomeflow.flow;

import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.action.ActionTrigger;
import studios.littlesquad.welcomeflow.objective.ObjectiveDefinition;

import java.util.List;
import java.util.Map;

/**
 * One micro-objective in a flow, with the actions attached to its lifecycle.
 *
 * @param id stable identifier, unique within the flow; used by
 *     {@code /wf skip}, the API and the per-step funnel counters, so renaming
 *     one resets its statistics
 * @param display checklist label, MiniMessage
 * @param objective what the player must do
 * @param hintAfterMillis idle time before {@code ON_STEP_PROGRESS} fires as a
 *     nudge, or {@code 0} to never hint
 * @param actions per-trigger action lists; missing triggers mean "do nothing"
 */
public record Step(
        String id,
        String display,
        ObjectiveDefinition objective,
        long hintAfterMillis,
        Map<ActionTrigger, List<Action>> actions) {

    public Step {
        actions = Map.copyOf(actions);
    }

    /** Never null, so callers can hand the result straight to ActionRunner. */
    public List<Action> actionsFor(ActionTrigger trigger) {
        return actions.getOrDefault(trigger, List.of());
    }

    public boolean hasHint() {
        return hintAfterMillis > 0;
    }

    /** Units required to finish this step. */
    public int requiredAmount() {
        return objective.amount();
    }
}
