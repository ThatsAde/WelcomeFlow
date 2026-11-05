package studios.littlesquad.welcomeflow.action;

import studios.littlesquad.welcomeflow.util.Scheduling;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs an action list in order, honouring {@code delay} entries without
 * blocking the main thread.
 *
 * <p>The list is walked until an action reports a delay; the remainder is
 * then handed to the scheduler as a continuation. That keeps a
 * {@code 'delay: 40'} between two messages from freezing the server for two
 * seconds, which a {@code Thread.sleep} would.
 *
 * <p>Each action is individually guarded. One misbehaving action logs a
 * warning and the list continues, rather than silently dropping the steps
 * after it: a broken {@code give-item} should not stop the player being
 * told they finished the tutorial.
 */
public final class ActionRunner {

    private final Scheduling scheduling;
    private final Logger logger;

    public ActionRunner(Scheduling scheduling, Logger logger) {
        this.scheduling = scheduling;
        this.logger = logger;
    }

    /** Runs {@code actions} from the start. Safe to call with an empty list. */
    public void run(List<Action> actions, ActionContext context) {
        run(actions, context, () -> { });
    }

    /** Calls completion after the complete list, including delayed continuations. */
    public void run(List<Action> actions, ActionContext context, Runnable completion) {
        if (actions == null || actions.isEmpty()) {
            completion.run();
            return;
        }
        runFrom(actions, 0, context, context.session().generation(), completion);
    }

    private void runFrom(List<Action> actions, int startIndex, ActionContext context, long generation, Runnable completion) {
        for (int index = startIndex; index < actions.size(); index++) {
            // A delayed continuation can resume after the player logged off.
            if (!context.player().isOnline() || context.session().generation() != generation) {
                return;
            }

            Action action = actions.get(index);
            try {
                action.execute(context);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Action " + action.getClass().getSimpleName()
                        + " failed for " + context.player().getName()
                        + " (flow '" + context.flowId() + "'); continuing with the rest of the list.", e);
            }

            long delay = action.delayTicksAfter();
            if (delay > 0) {
                int resumeAt = index + 1;
                if (resumeAt < actions.size()) {
                    scheduling.runSyncLater(() -> runFrom(actions, resumeAt, context, generation, completion), delay);
                } else {
                    scheduling.runSyncLater(() -> {
                        if (context.session().generation() == generation) completion.run();
                    }, delay);
                }
                return;
            }
        }
        completion.run();
    }
}
