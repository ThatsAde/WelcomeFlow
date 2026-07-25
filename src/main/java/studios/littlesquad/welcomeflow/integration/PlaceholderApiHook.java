package studios.littlesquad.welcomeflow.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Soft integration with PlaceholderAPI for <em>resolving other plugins'</em>
 * placeholders inside our config strings.
 *
 * <p>Exposing WelcomeFlow's own placeholders is the other half of the
 * integration and lands in M4 as a separate expansion class; this half is
 * needed earlier because the {@code conditional} action's
 * {@code placeholder:} condition depends on it.
 *
 * <p>PlaceholderAPI is an optional dependency. The class is referenced
 * reflectively-by-availability: we check {@code isPluginEnabled} once and
 * only then touch {@code PlaceholderAPI} types, so a server without it never
 * triggers a {@code NoClassDefFoundError}. That check is why
 * {@link #resolve} returns the input unchanged rather than throwing when PAPI
 * is absent.
 */
public final class PlaceholderApiHook {

    /**
     * Tri-state cache of availability: null = not yet checked. Resolved lazily
     * because plugins can enable in any order and we may be asked before PAPI
     * has finished loading.
     */
    private static volatile Boolean available;

    private PlaceholderApiHook() {
    }

    public static boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean present = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        available = present;
        return present;
    }

    /** Forces the next {@link #isAvailable()} to re-check, used by {@code /wf reload}. */
    public static void invalidate() {
        available = null;
    }

    /**
     * Expands any {@code %placeholder%} patterns in {@code text} for
     * {@code player}.
     *
     * @return the expanded text, or {@code text} unchanged when
     *     PlaceholderAPI is not installed or expansion fails
     */
    public static String resolve(Player player, String text) {
        if (text == null || text.isEmpty() || player == null || text.indexOf('%') < 0) {
            return text == null ? "" : text;
        }
        if (!isAvailable()) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (RuntimeException | LinkageError e) {
            // A broken third-party expansion must not break our flow.
            return text;
        }
    }
}
