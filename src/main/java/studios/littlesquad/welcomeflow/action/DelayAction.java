package studios.littlesquad.welcomeflow.action;

/**
 * Pauses the remainder of an action list.
 *
 * <p>Does nothing on execute; the pause is expressed through
 * {@link #delayTicksAfter()}, which {@link ActionRunner} honours by
 * scheduling the rest of the list instead of sleeping. Blocking the main
 * thread here would freeze the server for the duration.
 */
public final class DelayAction implements Action {

    private final long delayTicks;

    public DelayAction(long delayTicks) {
        this.delayTicks = Math.max(0L, delayTicks);
    }

    @Override
    public void execute(ActionContext context) {
        // Intentionally empty -- the delay is the effect.
    }

    @Override
    public long delayTicksAfter() {
        return delayTicks;
    }
}
