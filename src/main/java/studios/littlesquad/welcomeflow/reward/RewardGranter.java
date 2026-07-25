package studios.littlesquad.welcomeflow.reward;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.session.PlayerSession;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The "grant this exactly once" capability, separated from its
 * {@link RewardService} implementation.
 *
 * <p>The retention services decide <em>whether</em> a player is eligible;
 * this decides <em>how</em> a reward is paid and guarded. Splitting them lets
 * the eligibility rules -- the part that gates real payouts -- be tested
 * without a server, an action runner or a database.
 */
@FunctionalInterface
public interface RewardGranter {

    /**
     * Grants a reward exactly once.
     *
     * @param player recipient
     * @param session their live session
     * @param rewardKey stable name, e.g. {@code "second-join"}
     * @param actions what to run
     * @param markClaimed applied to the session before the actions run, so a
     *     failing action cannot make the reward claimable again
     * @param extras additional MiniMessage placeholders for the actions
     * @return true if granted, false if a guard blocked it
     */
    boolean grantOnce(Player player,
                      PlayerSession session,
                      String rewardKey,
                      List<Action> actions,
                      Consumer<PlayerSession> markClaimed,
                      Map<String, String> extras);
}
