package studios.littlesquad.welcomeflow.compat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sound playback via the Adventure {@link Sound} API.
 *
 * <p>{@code Player#playSound(Location/Entity, org.bukkit.Sound, float, float)}
 * was removed in paper-api 26.x. {@link Audience#playSound(Sound)} is
 * present and unchanged in both 1.21.4 and 26.1.2, so everything in this
 * class routes through it instead. Nothing outside {@code compat} should
 * reference the legacy overload -- {@code compileVerify26Java} (see
 * build.gradle.kts) fails the build if it does.
 *
 * <p>Resolution deliberately avoids {@code Registry#match(String)} and
 * {@code Keyed#getKey()}: both are deprecated <em>for removal</em>, so
 * leaning on them inside the very layer that exists to dodge removed APIs
 * would just relocate the problem. Only {@code Registry#stream()},
 * {@code Registry#getKey(T)} and {@code Registry#get(NamespacedKey)} are
 * used -- all three are stable and identical across both target versions.
 */
public final class Sounds {

    /**
     * Uppercased enum-style alias -> registry key, mirroring the server's
     * sound registry. Built lazily on first use because the registry is not
     * populated until the server is up, then never mutated.
     *
     * <p>Volatile + immutable-on-publish: {@link #resolve(String)} is
     * reachable from async config validation as well as the main thread.
     */
    private static volatile Map<String, NamespacedKey> aliasIndex;

    private Sounds() {
    }

    /**
     * Resolves a sound as written in flows.yml to its registry key.
     *
     * <p>Three spellings are accepted, so existing configs and
     * resource-pack sounds both keep working:
     * <ul>
     *   <li>Bukkit enum style -- {@code ENTITY_PLAYER_LEVELUP}</li>
     *   <li>Registry path -- {@code entity.player.levelup}</li>
     *   <li>Fully qualified -- {@code minecraft:entity.player.levelup},
     *       or a custom namespace from a resource pack</li>
     * </ul>
     *
     * @return the resolved key, or {@code null} if no such sound is
     *     registered -- callers surface that to the user via ConfigValidator
     *     rather than throwing.
     */
    public static NamespacedKey resolve(String bukkitSoundName) {
        if (bukkitSoundName == null || bukkitSoundName.isBlank()) {
            return null;
        }
        String input = bukkitSoundName.trim();

        // Explicit namespace, or a registry path: take it at face value and
        // let the registry confirm it exists.
        if (input.indexOf(':') >= 0 || input.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT));
            if (key == null) {
                return null;
            }
            return Registry.SOUNDS.get(key) == null ? null : key;
        }

        // Enum style: look it up in the inverted registry index.
        return aliasIndex().get(input.toUpperCase(Locale.ROOT));
    }

    public static void play(Audience audience, NamespacedKey key, Sound.Source source, float volume, float pitch) {
        audience.playSound(Sound.sound(key, source, volume, pitch));
    }

    public static void play(Audience audience, NamespacedKey key, Sound.Source source, float volume, float pitch,
                             Location origin) {
        audience.playSound(Sound.sound(key, source, volume, pitch), origin.getX(), origin.getY(), origin.getZ());
    }

    /**
     * Builds (once) the enum-alias index by walking the sound registry.
     *
     * <p>The mapping runs registry-key -> enum-name, never the reverse:
     * {@code block.note_block.harp} becomes {@code BLOCK_NOTE_BLOCK_HARP},
     * which is exactly how Bukkit derives its Sound constant names. Going
     * the other way is ambiguous -- naively swapping {@code _} for
     * {@code .} would turn {@code BLOCK_NOTE_BLOCK_HARP} into the
     * non-existent {@code block.note.block.harp}.
     */
    private static Map<String, NamespacedKey> aliasIndex() {
        Map<String, NamespacedKey> index = aliasIndex;
        if (index != null) {
            return index;
        }
        synchronized (Sounds.class) {
            if (aliasIndex != null) {
                return aliasIndex;
            }
            Map<String, NamespacedKey> built = new HashMap<>();
            Registry.SOUNDS.stream().forEach(sound -> {
                NamespacedKey key = Registry.SOUNDS.getKey(sound);
                if (key != null) {
                    built.putIfAbsent(toAlias(key), key);
                }
            });
            aliasIndex = Map.copyOf(built);
            return aliasIndex;
        }
    }

    private static String toAlias(NamespacedKey key) {
        return key.getKey().replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
