package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player qualifies for a retention reward, before the reward's
 * actions run.
 *
 * <p><strong>Cancellable</strong>, and cancelling genuinely withholds the
 * payout -- but note the claim has <em>already</em> been marked by the time
 * handlers run. That ordering is deliberate: the claim guard must be set
 * before anything can fail, or a thrown handler would leave the reward
 * payable twice. The practical consequence is that cancelling means "this
 * player forfeits this reward", not "ask me again next login".
 *
 * <pre>{@code
 * @EventHandler
 * public void onReward(RetentionRewardEvent event) {
 *     if (event.getType() == RetentionRewardEvent.Type.RETURN_BONUS
 *             && isBanned(event.getPlayer())) {
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 */
public class RetentionRewardEvent extends Event implements Cancellable {

    /** Which retention reward this is. */
    public enum Type {
        /** Granted on the configured Nth login. */
        SECOND_JOIN,
        /** Granted on returning after enough time away. */
        RETURN_BONUS
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Type type;
    private final int claimNumber;
    private boolean cancelled;

    /**
     * @param claimNumber which claim this is for the player -- always 1 for
     *     SECOND_JOIN, incrementing for RETURN_BONUS
     */
    public RetentionRewardEvent(Player player, Type type, int claimNumber) {
        this.player = player;
        this.type = type;
        this.claimNumber = claimNumber;
    }

    public Player getPlayer() {
        return player;
    }

    public Type getType() {
        return type;
    }

    public int getClaimNumber() {
        return claimNumber;
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
