package studios.littlesquad.welcomeflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Durations appear all over flows.yml ({@code hint-after: 45s},
 * {@code 'delay: 2m'}), and a silent misparse would change timing rather than
 * fail visibly -- hence the explicit "-1 means unparseable" contract that
 * callers turn into a config error.
 */
@DisplayName("Durations")
class DurationsTest {

    @Test
    @DisplayName("suffixes convert correctly")
    void suffixes() {
        assertEquals(1_000L, Durations.parseMillis("1s"));
        assertEquals(45_000L, Durations.parseMillis("45s"));
        assertEquals(120_000L, Durations.parseMillis("2m"));
        assertEquals(3_600_000L, Durations.parseMillis("1h"));
        assertEquals(50L, Durations.parseMillis("1t"));
    }

    @Test
    @DisplayName("a bare number is ticks, matching parseTicks")
    void bareNumberIsTicks() {
        assertEquals(2_000L, Durations.parseMillis("40"));
        assertEquals(40L, Durations.parseTicks("40"));
    }

    @Test
    @DisplayName("parseTicks agrees with parseMillis")
    void ticksAgreeWithMillis() {
        assertEquals(20L, Durations.parseTicks("1s"));
        assertEquals(900L, Durations.parseTicks("45s"));
        assertEquals(1L, Durations.parseTicks("1t"));
    }

    @Test
    @DisplayName("case and whitespace are tolerated")
    void toleratesFormatting() {
        assertEquals(45_000L, Durations.parseMillis(" 45S "));
        assertEquals(120_000L, Durations.parseMillis("2 M"));
    }

    @Test
    @DisplayName("fractional values are supported")
    void fractional() {
        assertEquals(1_500L, Durations.parseMillis("1.5s"));
        assertEquals(30_000L, Durations.parseMillis("0.5m"));
    }

    @Test
    @DisplayName("unparseable input returns -1 rather than a silent default")
    void unparseableReturnsMinusOne() {
        assertEquals(-1L, Durations.parseMillis("soon"));
        assertEquals(-1L, Durations.parseMillis(""));
        assertEquals(-1L, Durations.parseMillis(null));
        assertEquals(-1L, Durations.parseMillis("s"));
        assertEquals(-1L, Durations.parseMillis("-5s"));
        assertEquals(-1L, Durations.parseTicks("nope"));
    }

    @Test
    @DisplayName("humanize renders the largest sensible units")
    void humanizeReadsNaturally() {
        assertEquals("0s", Durations.humanize(0));
        assertEquals("0s", Durations.humanize(-1));
        assertEquals("45s", Durations.humanize(45_000L));
        assertEquals("2m", Durations.humanize(120_000L));
        assertEquals("2m 30s", Durations.humanize(150_000L));
        assertEquals("1h", Durations.humanize(3_600_000L));
        assertEquals("2h 15m", Durations.humanize(8_100_000L));
    }
}
