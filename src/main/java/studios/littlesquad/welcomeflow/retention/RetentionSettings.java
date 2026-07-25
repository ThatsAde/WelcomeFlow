package studios.littlesquad.welcomeflow.retention;

import studios.littlesquad.welcomeflow.config.ConfigManager;

/**
 * The retention knobs, lifted out of {@link ConfigManager} into a value.
 *
 * <p>The services need seven numbers, not the whole config object. Depending
 * on this instead means their rules are pure functions of plain data: the
 * eligibility tests can drive the clock and the thresholds directly, with no
 * Bukkit, no YAML and no mocking framework.
 *
 * @param secondJoinEnabled master toggle for the second-join reward
 * @param triggerJoinNumber which login earns it
 * @param minimumFirstSessionSeconds anti-farm floor on the first session
 * @param returnBonusEnabled master toggle for the return bonus
 * @param minimumHoursAway how long away before a return counts
 * @param cooldownHours minimum gap between two claims
 * @param maxClaims lifetime cap, {@code 0} for unlimited
 */
public record RetentionSettings(
        boolean secondJoinEnabled,
        int triggerJoinNumber,
        int minimumFirstSessionSeconds,
        boolean returnBonusEnabled,
        int minimumHoursAway,
        int cooldownHours,
        int maxClaims) {

    /** Snapshot of the current configuration. Re-read on reload. */
    public static RetentionSettings from(ConfigManager config) {
        return new RetentionSettings(
                config.secondJoinEnabled(),
                config.secondJoinTriggerJoinNumber(),
                config.secondJoinMinimumFirstSessionSeconds(),
                config.returnBonusEnabled(),
                config.returnBonusMinimumHoursAway(),
                config.returnBonusCooldownHours(),
                config.returnBonusMaxClaims());
    }
}
