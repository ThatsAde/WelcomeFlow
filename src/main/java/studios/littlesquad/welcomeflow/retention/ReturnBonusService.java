package studios.littlesquad.welcomeflow.retention;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.reward.RewardGranter;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.storage.PlayerRecord;
import studios.littlesquad.welcomeflow.util.Durations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Rewards a player who comes back after being away -- the recurring half of
 * the retention layer, and the one that keeps working past a player's second
 * session.
 *
 * <p>Three independent limits, all of which must pass:
 * <ul>
 *   <li><strong>Time away</strong> -- at least {@code minimum-hours-away}
 *       since their last logout. Measured from the {@code lastSeenAt} value
 *       captured at load time, <em>before</em> this join overwrote it.</li>
 *   <li><strong>Cooldown</strong> -- at least {@code cooldown-hours} since
 *       their previous claim. Without this a player could log out and back in
 *       every 12 hours forever; the cooldown lets an owner set a floor
 *       independent of the away requirement.</li>
 *   <li><strong>Max claims</strong> -- a lifetime cap, or unlimited at 0.</li>
 * </ul>
 */
public final class ReturnBonusService {

    /** Reward key used for guards, funnel counters and the API event. */
    public static final String REWARD_KEY = "return-bonus";

    private final Supplier<RetentionSettings> settings;
    private final RewardGranter rewardGranter;
    private final Supplier<List<Action>> actions;

    /**
     * @param settings re-read through a supplier so {@code /wf reload} takes
     *     effect without rebuilding the service
     */
    public ReturnBonusService(Supplier<RetentionSettings> settings, RewardGranter rewardGranter,
                              Supplier<List<Action>> actions) {
        this.settings = settings;
        this.rewardGranter = rewardGranter;
        this.actions = actions;
    }

    /**
     * Evaluates and, if eligible, grants the bonus.
     *
     * @return true if the bonus was granted this call
     */
    public boolean evaluate(Player player, PlayerSession session) {
        RetentionSettings current = settings.get();
        if (!current.returnBonusEnabled()) {
            return false;
        }
        long timeAway = session.timeAwayMillis();
        // Zero means a first-ever join: there is nothing to have returned from.
        if (timeAway <= 0) {
            return false;
        }
        if (timeAway < requiredAwayMillis(current)) {
            return false;
        }

        PlayerRecord record = session.record();
        if (isCapped(record, current)) {
            return false;
        }
        if (isOnCooldown(record, session.sessionStartedAt(), current)) {
            return false;
        }

        long claimedAt = session.sessionStartedAt();
        return rewardGranter.grantOnce(
                player,
                session,
                REWARD_KEY,
                actions.get(),
                claimed -> {
                    claimed.record().incrementReturnBonusClaims();
                    claimed.record().lastReturnBonusAt(claimedAt);
                },
                Map.of(
                        "time-away", Durations.humanize(timeAway),
                        "claims", String.valueOf(record.returnBonusClaims() + 1)));
    }

    private static long requiredAwayMillis(RetentionSettings settings) {
        return TimeUnit.HOURS.toMillis(Math.max(0, settings.minimumHoursAway()));
    }

    private static boolean isCapped(PlayerRecord record, RetentionSettings settings) {
        int max = settings.maxClaims();
        return max > 0 && record.returnBonusClaims() >= max;
    }

    private static boolean isOnCooldown(PlayerRecord record, long now, RetentionSettings settings) {
        long cooldown = TimeUnit.HOURS.toMillis(Math.max(0, settings.cooldownHours()));
        if (cooldown <= 0 || record.lastReturnBonusAt() <= 0) {
            return false;
        }
        return now - record.lastReturnBonusAt() < cooldown;
    }

    /**
     * Milliseconds until this player could next qualify, or {@code 0} if they
     * qualify now. Backs the {@code %welcomeflow_next_return_bonus%}
     * placeholder.
     *
     * @return {@code -1} when they can never qualify again (cap reached or
     *     the feature is off)
     */
    public long millisUntilEligible(PlayerSession session, long now) {
        RetentionSettings current = settings.get();
        if (!current.returnBonusEnabled()) {
            return -1L;
        }
        PlayerRecord record = session.record();
        if (isCapped(record, current)) {
            return -1L;
        }
        long cooldown = TimeUnit.HOURS.toMillis(Math.max(0, current.cooldownHours()));
        if (record.lastReturnBonusAt() <= 0 || cooldown <= 0) {
            return 0L;
        }
        long readyAt = record.lastReturnBonusAt() + cooldown;
        return Math.max(0L, readyAt - now);
    }

    /** Why a player is not currently eligible, for {@code /wf status}. */
    public String explain(PlayerSession session) {
        RetentionSettings current = settings.get();
        if (!current.returnBonusEnabled()) {
            return "disabled in config";
        }
        long timeAway = session.timeAwayMillis();
        if (timeAway <= 0) {
            return "first join, nothing to return from";
        }
        if (timeAway < requiredAwayMillis(current)) {
            return "away " + Durations.humanize(timeAway) + ", needs " + current.minimumHoursAway() + "h";
        }
        PlayerRecord record = session.record();
        if (isCapped(record, current)) {
            return "reached the " + current.maxClaims() + "-claim cap";
        }
        if (isOnCooldown(record, session.sessionStartedAt(), current)) {
            long remaining = millisUntilEligible(session, session.sessionStartedAt());
            return "on cooldown for another " + Durations.humanize(remaining);
        }
        return "eligible";
    }
}
