package studios.littlesquad.welcomeflow.retention;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.reward.RewardGranter;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.storage.PlayerRecord;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rewards a player's Nth login: the "they came back once" half of the
 * retention layer.
 *
 * <p>Two properties make this safe to attach real rewards to:
 *
 * <p><strong>It cannot be farmed by relogging.</strong> Eligibility requires
 * the player's <em>first</em> session to have lasted at least
 * {@code minimum-first-session-seconds}. That duration is banked once, on the
 * first logout, so joining and instantly quitting repeatedly never satisfies
 * it.
 *
 * <p><strong>Installing on an established server does not pay everyone at
 * once.</strong> The join counter is WelcomeFlow's own and starts at zero for
 * every player the first time we see them, so a veteran of 500 logins is at
 * count 1 after install and earns the reward on their genuine next return --
 * not immediately. This is why the check is {@code >=} rather than
 * {@code ==}: a player who logs in while the plugin is disabled should still
 * get it later, not be skipped forever.
 */
public final class SecondJoinService {

    /** Reward key used for guards, funnel counters and the API event. */
    public static final String REWARD_KEY = "second-join";

    private final Supplier<RetentionSettings> settings;
    private final RewardGranter rewardGranter;
    private final Supplier<List<Action>> actions;

    /**
     * @param settings re-read through a supplier so {@code /wf reload} takes
     *     effect without rebuilding the service
     */
    public SecondJoinService(Supplier<RetentionSettings> settings, RewardGranter rewardGranter,
                             Supplier<List<Action>> actions) {
        this.settings = settings;
        this.rewardGranter = rewardGranter;
        this.actions = actions;
    }

    /**
     * Evaluates and, if eligible, grants the reward.
     *
     * @return true if the reward was granted this call
     */
    public boolean evaluate(Player player, PlayerSession session) {
        RetentionSettings current = settings.get();
        if (!current.secondJoinEnabled()) {
            return false;
        }
        PlayerRecord record = session.record();

        // Guard layer 1: the persistent claim flag.
        if (record.secondJoinClaimed()) {
            return false;
        }
        if (record.joinCount() < current.triggerJoinNumber()) {
            return false;
        }
        if (!hasPlayedLongEnough(record, current)) {
            return false;
        }

        return rewardGranter.grantOnce(
                player,
                session,
                REWARD_KEY,
                actions.get(),
                claimed -> claimed.record().secondJoinClaimed(true),
                Map.of("join-count", String.valueOf(record.joinCount())));
    }

    /**
     * The anti-farm check.
     *
     * <p>{@code firstSessionSeconds} is written when the player's first
     * session ends. A player still inside their very first session therefore
     * has {@code 0} here, but they also have {@code joinCount == 1}, so they
     * never reach this check. Reading {@code 0} at join #2 or later genuinely
     * means "their first session was too short to have been recorded", which
     * correctly fails the requirement.
     */
    private boolean hasPlayedLongEnough(PlayerRecord record, RetentionSettings current) {
        int required = current.minimumFirstSessionSeconds();
        return required <= 0 || record.firstSessionSeconds() >= required;
    }

    /**
     * Why a player is not currently eligible, for {@code /wf status} and
     * debug logging.
     */
    public String explain(PlayerSession session) {
        RetentionSettings current = settings.get();
        if (!current.secondJoinEnabled()) {
            return "disabled in config";
        }
        PlayerRecord record = session.record();
        if (record.secondJoinClaimed()) {
            return "already claimed";
        }
        if (record.joinCount() < current.triggerJoinNumber()) {
            return "on join " + record.joinCount() + " of " + current.triggerJoinNumber();
        }
        if (!hasPlayedLongEnough(record, current)) {
            return "first session was " + record.firstSessionSeconds() + "s, needs "
                    + current.minimumFirstSessionSeconds() + "s";
        }
        return "eligible";
    }
}
