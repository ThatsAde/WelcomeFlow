package studios.littlesquad.welcomeflow.retention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.storage.PlayerRecord;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the eligibility rules for both retention rewards.
 *
 * <p>These are the highest-stakes decisions in the plugin: they gate real
 * payouts (economy commands, items), so a false positive costs the server
 * owner money and a false negative silently breaks the feature they bought
 * the plugin for.
 *
 * <p>The services are exercised through small hand-written fakes rather than
 * MockBukkit. The rules are pure arithmetic over a PlayerRecord, so a fake
 * keeps the tests fast and lets us drive the clock directly instead of
 * waiting real hours.
 */
@DisplayName("Retention eligibility")
class RetentionServiceTest {

    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    private MutableSettings config;
    private RecordingGranter rewards;

    @BeforeEach
    void setUp() {
        config = new MutableSettings();
        rewards = new RecordingGranter();
    }

    private static PlayerSession session(PlayerRecord record, long joinedAt, long previousLastSeen) {
        return new PlayerSession(record.uuid(), "Tester", record, null, Set.of(), joinedAt, previousLastSeen);
    }

    private static PlayerRecord record() {
        return new PlayerRecord(UUID.randomUUID());
    }

    // =====================================================================
    @Nested
    @DisplayName("second join")
    class SecondJoin {

        private SecondJoinService service() {
            return new SecondJoinService(config, rewards, List::of);
        }

        @Test
        @DisplayName("granted on the configured login once the first session was long enough")
        void grantedWhenEligible() {
            PlayerRecord record = record();
            record.joinCount(2);
            record.firstSessionSeconds(300);

            assertTrue(service().evaluate(null, session(record, 0L, 0L)));
            assertEquals(1, rewards.grants, "reward should have been granted exactly once");
        }

        @Test
        @DisplayName("not granted before the configured login")
        void notGrantedTooEarly() {
            PlayerRecord record = record();
            record.joinCount(1);
            record.firstSessionSeconds(300);

            assertFalse(service().evaluate(null, session(record, 0L, 0L)));
            assertEquals(0, rewards.grants);
        }

        @Test
        @DisplayName("granted on a later login too, so a player who joined while the plugin was off is not skipped")
        void grantedOnLaterLogin() {
            PlayerRecord record = record();
            record.joinCount(5);
            record.firstSessionSeconds(300);

            assertTrue(service().evaluate(null, session(record, 0L, 0L)));
        }

        @Test
        @DisplayName("cannot be farmed: a too-short first session blocks it")
        void shortFirstSessionIsBlocked() {
            PlayerRecord record = record();
            record.joinCount(2);
            record.firstSessionSeconds(5); // instant relog

            assertFalse(service().evaluate(null, session(record, 0L, 0L)));
            assertEquals(0, rewards.grants, "instant-relog farming must not pay out");
        }

        @Test
        @DisplayName("a zero minimum disables the anti-farm requirement")
        void zeroMinimumAllowsAnySession() {
            config.minimumFirstSessionSeconds = 0;
            PlayerRecord record = record();
            record.joinCount(2);
            record.firstSessionSeconds(0);

            assertTrue(service().evaluate(null, session(record, 0L, 0L)));
        }

        @Test
        @DisplayName("never granted twice -- the persistent claim flag")
        void neverGrantedTwice() {
            PlayerRecord record = record();
            record.joinCount(2);
            record.firstSessionSeconds(300);
            PlayerSession session = session(record, 0L, 0L);

            assertTrue(service().evaluate(null, session));
            assertFalse(service().evaluate(null, session), "second call must be blocked by the claim flag");
            assertEquals(1, rewards.grants, "a reward must never be paid twice");
        }

        @Test
        @DisplayName("respects the enabled toggle")
        void disabledMeansNoGrant() {
            config.secondJoinEnabled = false;
            PlayerRecord record = record();
            record.joinCount(2);
            record.firstSessionSeconds(300);

            assertFalse(service().evaluate(null, session(record, 0L, 0L)));
        }

        @Test
        @DisplayName("explain() names the actual blocker")
        void explainIsUseful() {
            PlayerRecord record = record();
            record.joinCount(1);
            assertTrue(service().explain(session(record, 0L, 0L)).contains("of 2"));

            record.joinCount(2);
            record.firstSessionSeconds(5);
            assertTrue(service().explain(session(record, 0L, 0L)).contains("needs"));

            record.firstSessionSeconds(300);
            assertEquals("eligible", service().explain(session(record, 0L, 0L)));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("return bonus")
    class ReturnBonus {

        private ReturnBonusService service() {
            return new ReturnBonusService(config, rewards, List::of);
        }

        @Test
        @DisplayName("granted after being away long enough")
        void grantedAfterEnoughTimeAway() {
            PlayerRecord record = record();
            long joinedAt = 100 * HOUR;
            PlayerSession session = session(record, joinedAt, joinedAt - 13 * HOUR);

            assertTrue(service().evaluate(null, session));
            assertEquals(1, rewards.grants);
        }

        @Test
        @DisplayName("not granted when they were away for less than the minimum")
        void notGrantedTooSoon() {
            PlayerRecord record = record();
            long joinedAt = 100 * HOUR;
            PlayerSession session = session(record, joinedAt, joinedAt - 3 * HOUR);

            assertFalse(service().evaluate(null, session));
        }

        @Test
        @DisplayName("a first-ever join is not a return")
        void firstJoinIsNotAReturn() {
            assertFalse(service().evaluate(null, session(record(), 100 * HOUR, 0L)));
        }

        @Test
        @DisplayName("cooldown blocks a second claim even when they qualify again")
        void cooldownBlocksRepeatClaims() {
            PlayerRecord record = record();
            long joinedAt = 100 * HOUR;
            // Away long enough, but claimed only 5h ago against a 20h cooldown.
            record.lastReturnBonusAt(joinedAt - 5 * HOUR);
            record.returnBonusClaims(1);

            assertFalse(service().evaluate(null, session(record, joinedAt, joinedAt - 13 * HOUR)));
        }

        @Test
        @DisplayName("granted again once the cooldown has passed")
        void grantedAfterCooldown() {
            PlayerRecord record = record();
            long joinedAt = 100 * HOUR;
            record.lastReturnBonusAt(joinedAt - 25 * HOUR);
            record.returnBonusClaims(1);

            assertTrue(service().evaluate(null, session(record, joinedAt, joinedAt - 13 * HOUR)));
            assertEquals(2, record.returnBonusClaims());
        }

        @Test
        @DisplayName("max-claims caps lifetime payouts")
        void maxClaimsIsRespected() {
            config.maxClaims = 3;
            PlayerRecord record = record();
            record.returnBonusClaims(3);
            long joinedAt = 100 * HOUR;

            assertFalse(service().evaluate(null, session(record, joinedAt, joinedAt - 50 * HOUR)));
        }

        @Test
        @DisplayName("max-claims of 0 means unlimited")
        void zeroMaxClaimsIsUnlimited() {
            config.maxClaims = 0;
            PlayerRecord record = record();
            record.returnBonusClaims(999);
            long joinedAt = 100 * HOUR;

            assertTrue(service().evaluate(null, session(record, joinedAt, joinedAt - 50 * HOUR)));
        }

        @Test
        @DisplayName("claim count and timestamp are both recorded")
        void claimStateIsRecorded() {
            PlayerRecord record = record();
            long joinedAt = 100 * HOUR;

            service().evaluate(null, session(record, joinedAt, joinedAt - 13 * HOUR));

            assertEquals(1, record.returnBonusClaims());
            assertEquals(joinedAt, record.lastReturnBonusAt(),
                    "timestamp must be the join time, so the next cooldown measures from here");
        }

        @Test
        @DisplayName("millisUntilEligible reports the cooldown remainder")
        void reportsTimeUntilEligible() {
            PlayerRecord record = record();
            long now = 100 * HOUR;
            record.lastReturnBonusAt(now - 5 * HOUR);

            assertEquals(15 * HOUR, service().millisUntilEligible(session(record, now, 0L), now));
        }

        @Test
        @DisplayName("millisUntilEligible is -1 when capped out")
        void reportsNeverWhenCapped() {
            config.maxClaims = 1;
            PlayerRecord record = record();
            record.returnBonusClaims(1);

            assertEquals(-1L, service().millisUntilEligible(session(record, 0L, 0L), 0L));
        }

        @Test
        @DisplayName("respects the enabled toggle")
        void disabledMeansNoGrant() {
            config.returnBonusEnabled = false;
            long joinedAt = 100 * HOUR;
            assertFalse(service().evaluate(null, session(record(), joinedAt, joinedAt - 50 * HOUR)));
        }
    }

    // =====================================================================

    /**
     * Mutable holder that hands the services a fresh {@link RetentionSettings}
     * each call, so a test can change a threshold mid-scenario the same way
     * {@code /wf reload} does.
     */
    private static final class MutableSettings implements java.util.function.Supplier<RetentionSettings> {

        boolean secondJoinEnabled = true;
        int triggerJoinNumber = 2;
        int minimumFirstSessionSeconds = 120;
        boolean returnBonusEnabled = true;
        int minimumHoursAway = 12;
        int cooldownHours = 20;
        int maxClaims = 0;

        @Override
        public RetentionSettings get() {
            return new RetentionSettings(secondJoinEnabled, triggerJoinNumber, minimumFirstSessionSeconds,
                    returnBonusEnabled, minimumHoursAway, cooldownHours, maxClaims);
        }
    }

    /**
     * Applies the claim mutation and counts grants, without touching Bukkit.
     * Mirrors the real granter's "mark claimed, then run actions" ordering,
     * which is what makes the double-claim assertions meaningful.
     */
    private static final class RecordingGranter
            implements studios.littlesquad.welcomeflow.reward.RewardGranter {

        int grants;

        @Override
        public boolean grantOnce(org.bukkit.entity.Player player,
                                 PlayerSession session,
                                 String rewardKey,
                                 List<studios.littlesquad.welcomeflow.action.Action> actions,
                                 java.util.function.Consumer<PlayerSession> markClaimed,
                                 java.util.Map<String, String> extras) {
            markClaimed.accept(session);
            grants++;
            return true;
        }
    }
}
