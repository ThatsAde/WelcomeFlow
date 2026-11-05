package studios.littlesquad.welcomeflow.objective;

import java.util.Locale;
import java.util.Set;

/**
 * The kinds of objective a step can require.
 *
 * <p>Each constant declares which extra config keys it understands, so
 * ConfigValidator can flag both missing required keys and keys that will be
 * silently ignored: a step with {@code type: SEND_CHAT} and a
 * {@code material:} line is almost certainly a copy-paste mistake worth
 * reporting.
 *
 * <p>{@link #listenerless()} marks the types that are not driven by a Bukkit
 * event, which is what lets the plugin register listeners only for the types
 * a loaded flow actually uses.
 */
public enum ObjectiveType {

    /** Blocks travelled, accumulated from movement; counts whole blocks. */
    WALK_DISTANCE(Set.of(), Set.of()),

    /** Blocks broken, optionally filtered by {@code material}. */
    BREAK_BLOCK(Set.of(), Set.of("material")),

    /** Blocks placed, optionally filtered by {@code material}. */
    PLACE_BLOCK(Set.of(), Set.of("material")),

    /** Items crafted, optionally filtered by {@code material}. */
    CRAFT_ITEM(Set.of(), Set.of("material")),

    /** Items picked up, optionally filtered by {@code material}. */
    PICKUP_ITEM(Set.of(), Set.of("material")),

    /** Mobs killed, optionally filtered by {@code entity}. */
    KILL_ENTITY(Set.of(), Set.of("entity")),

    /** A command matching {@code pattern} was run. */
    RUN_COMMAND(Set.of("pattern"), Set.of()),

    /** A chat message was sent. */
    SEND_CHAT(Set.of(), Set.of()),

    /** Any inventory was opened. */
    OPEN_INVENTORY(Set.of(), Set.of()),

    /** A block was right-clicked, optionally filtered by {@code material}. */
    INTERACT_BLOCK(Set.of(), Set.of("material")),

    /**
     * The player got within {@code radius} of a named location.
     * Checked on movement, so it needs no polling task.
     */
    REACH_LOCATION(Set.of("location"), Set.of("radius")),

    /** Seconds spent online during the current session. */
    PLAY_TIME(Set.of(), Set.of()),

    /** Advanced only via the API or {@code /wf skip}, so it has no listener. */
    MANUAL(Set.of(), Set.of());

    private final Set<String> requiredKeys;
    private final Set<String> optionalKeys;

    ObjectiveType(Set<String> requiredKeys, Set<String> optionalKeys) {
        this.requiredKeys = requiredKeys;
        this.optionalKeys = optionalKeys;
    }

    public Set<String> requiredKeys() {
        return requiredKeys;
    }

    public Set<String> optionalKeys() {
        return optionalKeys;
    }

    /** Every key valid for this type, excluding the universal {@code type}/{@code amount}. */
    public Set<String> allKeys() {
        if (optionalKeys.isEmpty()) {
            return requiredKeys;
        }
        if (requiredKeys.isEmpty()) {
            return optionalKeys;
        }
        Set<String> combined = new java.util.HashSet<>(requiredKeys);
        combined.addAll(optionalKeys);
        return Set.copyOf(combined);
    }

    /**
     * True for types with no backing Bukkit event.
     *
     * <p>{@link #MANUAL} is driven by the API, and {@link #PLAY_TIME} by the
     * session clock rather than an event, so neither contributes a listener.
     */
    public boolean listenerless() {
        return this == MANUAL || this == PLAY_TIME;
    }

    /** @return the matching type, or {@code null} when the name is unknown. */
    public static ObjectiveType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
