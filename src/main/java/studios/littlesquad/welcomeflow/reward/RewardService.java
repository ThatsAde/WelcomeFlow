package studios.littlesquad.welcomeflow.reward;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.action.ActionContext;
import studios.littlesquad.welcomeflow.action.ActionRunner;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.session.SessionManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Grants retention rewards, with the double-claim guards that make paying out
 * real currency safe.
 *
 * <p>There are deliberately <em>two</em> layers of guard:
 * <ol>
 *   <li><strong>Persistent</strong>: a flag on the player's record, checked
 *       by the calling service and set here. This survives restarts and is
 *       the real guarantee.</li>
 *   <li><strong>In-flight</strong>: an in-memory set keyed by player and
 *       reward. The persistent flag is only durable once the async flush
 *       completes; without this, two grants in the same tick could both
 *       observe an unclaimed flag and both pay out.</li>
 * </ol>
 *
 * <p>The claim is marked and the write requested <em>before</em> the reward
 * actions run, so an action that throws cannot leave the reward claimable
 * again: paying twice is far worse than a player missing a title.
 *
 * <p>Main thread only, which is what makes the plain {@link HashSet} safe.
 */
public final class RewardService implements RewardGranter {

    private final ActionRunner actionRunner;
    private final SessionManager sessionManager;
    private final Logger logger;

    /** Guards a second grant landing before the first has persisted. */
    private final Set<String> inFlight = new HashSet<>();

    /** Notified after a successful grant, for funnel counters. */
    private final Consumer<String> onGranted;

    /**
     * Fires the cancellable public event; returns true to withhold the
     * reward's actions. Injected rather than called directly so the reward
     * logic stays testable without a running server.
     */
    private final BiPredicate<Player, String> vetoed;

    public RewardService(ActionRunner actionRunner, SessionManager sessionManager, Logger logger,
                         Consumer<String> onGranted, BiPredicate<Player, String> vetoed) {
        this.actionRunner = actionRunner;
        this.sessionManager = sessionManager;
        this.logger = logger;
        this.onGranted = onGranted;
        this.vetoed = vetoed;
    }

    /**
     * Grants a reward exactly once.
     *
     * @param player recipient
     * @param session their live session
     * @param rewardKey stable name, e.g. {@code "second-join"}; used for the
     *     in-flight guard, the funnel counter and the log line
     * @param actions what to run
     * @param markClaimed applied to the session before the actions run
     * @param extras additional MiniMessage placeholders for the actions
     * @return true if granted, false if a guard blocked it
     */
    @Override
    public boolean grantOnce(Player player,
                             PlayerSession session,
                             String rewardKey,
                             List<Action> actions,
                             Consumer<PlayerSession> markClaimed,
                             Map<String, String> extras) {

        String guardKey = session.uuid() + "/" + rewardKey;
        if (!inFlight.add(guardKey)) {
            logger.fine("Ignoring duplicate '" + rewardKey + "' grant for " + player.getName()
                    + "; one is already in flight.");
            return false;
        }

        try {
            markClaimed.accept(session);
            session.markDirty();
            // Written now rather than at the next periodic flush: a crash
            // seconds after a payout must not resurrect the claim.
            sessionManager.flush(session);

            // Asked after the claim is marked, deliberately. Marking first is
            // what makes a thrown handler unable to leave the reward payable
            // twice; the trade is that cancelling means "forfeit", not "ask
            // again next login". Documented on RetentionRewardEvent.
            if (vetoed.test(player, rewardKey)) {
                logger.fine("Reward '" + rewardKey + "' for " + player.getName()
                        + " was cancelled by another plugin; actions skipped.");
                return false;
            }

            if (!actions.isEmpty()) {
                actionRunner.run(actions, ActionContext.ofFlow(player, session, rewardKey, extras));
            }

            onGranted.accept(rewardKey);
            logger.fine("Granted '" + rewardKey + "' to " + player.getName() + ".");
            return true;
        } finally {
            // Released once the synchronous part is done: the persistent flag
            // is set in memory and queued, so any later attempt stops at the
            // first guard layer.
            inFlight.remove(guardKey);
        }
    }

    /** Drops in-flight state for a player, e.g. after {@code /wf reset}. */
    public void forget(UUID uuid) {
        String prefix = uuid + "/";
        inFlight.removeIf(key -> key.startsWith(prefix));
    }
}
