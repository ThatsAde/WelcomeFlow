package studios.littlesquad.welcomeflow.storage;

import java.util.UUID;

/**
 * Per-player persistent counters that are not tied to any single flow:
 * join history plus the retention-reward claim guards.
 *
 * <p>Mutable by design. One instance is owned by the player's
 * {@code PlayerSession} for the duration of their session and flushed
 * asynchronously; copying on every counter bump would buy nothing.
 *
 * <p>All timestamps are epoch milliseconds, and {@code 0} means "never".
 */
public final class PlayerRecord {

    private final UUID uuid;
    private int joinCount;
    private long firstJoinAt;
    private long lastSeenAt;
    private long firstSessionSeconds;
    private boolean secondJoinClaimed;
    private int returnBonusClaims;
    private long lastReturnBonusAt;

    public PlayerRecord(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerRecord(UUID uuid, int joinCount, long firstJoinAt, long lastSeenAt, long firstSessionSeconds,
                        boolean secondJoinClaimed, int returnBonusClaims, long lastReturnBonusAt) {
        this.uuid = uuid;
        this.joinCount = joinCount;
        this.firstJoinAt = firstJoinAt;
        this.lastSeenAt = lastSeenAt;
        this.firstSessionSeconds = firstSessionSeconds;
        this.secondJoinClaimed = secondJoinClaimed;
        this.returnBonusClaims = returnBonusClaims;
        this.lastReturnBonusAt = lastReturnBonusAt;
    }

    public UUID uuid() {
        return uuid;
    }

    /** True when this player has never been seen before (no stored row). */
    public boolean isFirstJoin() {
        return joinCount <= 1;
    }

    public int joinCount() {
        return joinCount;
    }

    public void joinCount(int joinCount) {
        this.joinCount = joinCount;
    }

    public void incrementJoinCount() {
        this.joinCount++;
    }

    public long firstJoinAt() {
        return firstJoinAt;
    }

    public void firstJoinAt(long firstJoinAt) {
        this.firstJoinAt = firstJoinAt;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    public void lastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    /**
     * Seconds the player was online during their very first session --
     * the anti-farm guard for the second-join reward.
     */
    public long firstSessionSeconds() {
        return firstSessionSeconds;
    }

    public void firstSessionSeconds(long firstSessionSeconds) {
        this.firstSessionSeconds = firstSessionSeconds;
    }

    public boolean secondJoinClaimed() {
        return secondJoinClaimed;
    }

    public void secondJoinClaimed(boolean secondJoinClaimed) {
        this.secondJoinClaimed = secondJoinClaimed;
    }

    public int returnBonusClaims() {
        return returnBonusClaims;
    }

    public void returnBonusClaims(int returnBonusClaims) {
        this.returnBonusClaims = returnBonusClaims;
    }

    public void incrementReturnBonusClaims() {
        this.returnBonusClaims++;
    }

    public long lastReturnBonusAt() {
        return lastReturnBonusAt;
    }

    public void lastReturnBonusAt(long lastReturnBonusAt) {
        this.lastReturnBonusAt = lastReturnBonusAt;
    }
}
