package studios.littlesquad.welcomeflow.util;

import java.util.Locale;

/**
 * Parses the human-friendly durations used throughout flows.yml
 * ({@code hint-after: 45s}, {@code 'delay: 2m'}).
 *
 * <p>Returns {@code -1} on anything unparseable rather than throwing or
 * silently defaulting, so callers can produce a validation error that names
 * the offending value.
 */
public final class Durations {

    private static final long TICKS_PER_SECOND = 20L;

    private Durations() {
    }

    /**
     * Parses a duration into ticks.
     *
     * <p>Accepted forms: a bare number (already ticks), or a number with a
     * {@code t} (ticks), {@code s} (seconds), {@code m} (minutes) or
     * {@code h} (hours) suffix. Whitespace and case are ignored.
     *
     * @return ticks, or {@code -1} if the input could not be parsed
     */
    public static long parseTicks(String raw) {
        long millis = parseMillis(raw);
        return millis < 0 ? -1L : millis / 50L;
    }

    /**
     * Parses a duration into milliseconds.
     *
     * <p>A bare number is interpreted as <em>ticks</em> here too, for
     * consistency with {@link #parseTicks}: {@code 40} means 40 ticks
     * (2000ms), not 40ms. Unit suffixes remove the ambiguity, and the bundled
     * config always uses them.
     *
     * @return milliseconds, or {@code -1} if the input could not be parsed
     */
    public static long parseMillis(String raw) {
        if (raw == null) {
            return -1L;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.isEmpty()) {
            return -1L;
        }

        char suffix = value.charAt(value.length() - 1);
        boolean hasSuffix = suffix == 't' || suffix == 's' || suffix == 'm' || suffix == 'h';
        String numberPart = hasSuffix ? value.substring(0, value.length() - 1) : value;
        if (numberPart.isEmpty()) {
            return -1L;
        }

        double amount;
        try {
            amount = Double.parseDouble(numberPart);
        } catch (NumberFormatException e) {
            return -1L;
        }
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return -1L;
        }

        if (!hasSuffix) {
            return Math.round(amount * 50.0);
        }
        return switch (suffix) {
            case 't' -> Math.round(amount * 50.0);
            case 's' -> Math.round(amount * 1000.0);
            case 'm' -> Math.round(amount * 60_000.0);
            case 'h' -> Math.round(amount * 3_600_000.0);
            default -> -1L;
        };
    }

    /** Ticks for a whole number of seconds. */
    public static long secondsToTicks(long seconds) {
        return Math.max(0L, seconds) * TICKS_PER_SECOND;
    }

    /**
     * Formats a millisecond span the way {@code /wf status} and the
     * {@code %welcomeflow_next_return_bonus%} placeholder show it, e.g.
     * {@code "2h 15m"} or {@code "45s"}.
     */
    public static String humanize(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0) {
            return seconds > 0 ? minutes + "m " + seconds + "s" : minutes + "m";
        }
        return seconds + "s";
    }
}
