package studios.littlesquad.welcomeflow.objective;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Locale;

/**
 * A step's objective, fully resolved at config load time.
 *
 * <p>Unused fields are {@code null} / {@code 0} for the given
 * {@link ObjectiveType}. Resolving materials and entity types here rather
 * than on every event means a typo is a startup error instead of a per-event
 * cost, and the hot path in the listeners is a couple of reference
 * comparisons.
 *
 * @param type which kind of objective this is
 * @param amount how many units are needed; always at least 1
 * @param material material filter, or {@code null} for "any"
 * @param entityType entity filter, or {@code null} for "any"
 * @param pattern command pattern for {@link ObjectiveType#RUN_COMMAND}
 * @param locationName named location for {@link ObjectiveType#REACH_LOCATION}
 * @param radius radius in blocks for {@link ObjectiveType#REACH_LOCATION}
 */
public record ObjectiveDefinition(
        ObjectiveType type,
        int amount,
        Material material,
        EntityType entityType,
        String pattern,
        String locationName,
        double radius) {

    /** A minimal definition for types that need no filters. */
    public static ObjectiveDefinition of(ObjectiveType type, int amount) {
        return new ObjectiveDefinition(type, Math.max(1, amount), null, null, null, null, 0.0);
    }

    /** True when no material filter is set, or the filter matches. */
    public boolean matchesMaterial(Material candidate) {
        return material == null || material == candidate;
    }

    /** True when no entity filter is set, or the filter matches. */
    public boolean matchesEntity(EntityType candidate) {
        return entityType == null || entityType == candidate;
    }

    /**
     * Matches a command the player ran against this objective's pattern.
     *
     * <p>Comparison is case-insensitive, ignores a leading slash on either
     * side, and collapses repeated whitespace. A pattern ending in {@code *}
     * matches by prefix.
     *
     * <p>This normalisation is deliberate rather than incidental: without it
     * {@code /Kit Starter} would silently fail to satisfy a
     * {@code pattern: "/kit starter"} step, and the player would be stuck on
     * a tutorial step with no idea why.
     */
    public boolean matchesCommand(String rawCommand) {
        if (pattern == null || rawCommand == null) {
            return false;
        }
        String candidate = normaliseCommand(rawCommand);
        String expected = normaliseCommand(pattern);
        if (expected.endsWith("*")) {
            String prefix = expected.substring(0, expected.length() - 1).trim();
            return candidate.startsWith(prefix);
        }
        return candidate.equals(expected);
    }

    private static String normaliseCommand(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
