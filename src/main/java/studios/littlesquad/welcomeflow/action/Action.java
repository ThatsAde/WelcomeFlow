package studios.littlesquad.welcomeflow.action;

/**
 * One configured action, already parsed and validated at load time.
 *
 * <p>Instances are immutable and shared across every player that runs the
 * flow, so implementations must keep no per-player state -- everything
 * player-specific arrives in the {@link ActionContext}.
 *
 * <p>Called on the main thread. Implementations must not block; anything
 * time-based goes through {@link DelayAction} and the runner's continuation
 * mechanism instead of sleeping.
 */
public interface Action {

    /**
     * Runs this action.
     *
     * <p>Implementations should be defensive: a bad material or an offline
     * player must not throw, because a single failing action would otherwise
     * abort the rest of the list. {@link ActionRunner} also guards each call,
     * but failing softly with a logged warning gives the owner a better
     * message than a stacktrace.
     */
    void execute(ActionContext context);

    /**
     * How long the runner must wait <em>after</em> this action before running
     * the next one, in ticks. Only {@link DelayAction} returns non-zero; it
     * is expressed here so the runner needs no instanceof check.
     */
    default long delayTicksAfter() {
        return 0L;
    }
}
