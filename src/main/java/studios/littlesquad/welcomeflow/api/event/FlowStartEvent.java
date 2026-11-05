package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a flow is about to start for a player.
 *
 * <p><strong>Cancellable.</strong> This fires <em>before</em> any state is
 * written, so cancelling leaves the player with no active flow at all; use
 * it to exempt staff, VIPs, or players arriving from a lobby that already
 * onboarded them.
 *
 * <p>Cancelling does not prevent the flow being selected again on their next
 * login; to opt a player out permanently, give them a permission your flow's
 * conditions exclude.
 *
 * <pre>{@code
 * @EventHandler
 * public void onStart(FlowStartEvent event) {
 *     if (event.getPlayer().hasPermission("myserver.skiptutorial")) {
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 */
public class FlowStartEvent extends WelcomeFlowEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int stepCount;
    private final boolean resumed;
    private boolean cancelled;

    /**
     * @param stepCount how many steps the flow has
     * @param resumed true when picking up an interrupted run rather than
     *     starting fresh
     */
    public FlowStartEvent(Player player, String flowId, int stepCount, boolean resumed) {
        super(player, flowId);
        this.stepCount = stepCount;
        this.resumed = resumed;
    }

    public int getStepCount() {
        return stepCount;
    }

    /** True when resuming an interrupted run after a relog or restart. */
    public boolean isResumed() {
        return resumed;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
