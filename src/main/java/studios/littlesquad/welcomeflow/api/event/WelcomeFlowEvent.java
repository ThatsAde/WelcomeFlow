package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base class for every WelcomeFlow event.
 *
 * <p>All events fire on the main thread, after the state change they
 * describe has been applied, with the exception of the cancellable ones,
 * which fire before. Check each event's own documentation.
 *
 * <p>Example:
 * <pre>{@code
 * @EventHandler
 * public void onFlowComplete(FlowCompleteEvent event) {
 *     getLogger().info(event.getPlayer().getName() + " finished " + event.getFlowId());
 * }
 * }</pre>
 */
public abstract class WelcomeFlowEvent extends Event {

    private final Player player;
    private final String flowId;

    protected WelcomeFlowEvent(Player player, String flowId) {
        this.player = player;
        this.flowId = flowId;
    }

    /** The player this event concerns. Always online when the event fires. */
    public Player getPlayer() {
        return player;
    }

    /** The flow's id as written in flows.yml. */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Required by Bukkit's event system.
     *
     * <p>Subclasses each declare their own static {@code HandlerList}; this
     * abstract override just forces them to.
     */
    @Override
    public abstract HandlerList getHandlers();
}
