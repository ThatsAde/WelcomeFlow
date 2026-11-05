package studios.littlesquad.welcomeflow.compat;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Config-string to game-object lookups, in one place.
 *
 * <p>Every method returns {@code null} rather than throwing on an unknown
 * name. That is deliberate: ConfigValidator turns a {@code null} into an
 * actionable "unknown material 'OAK_LOGG' at flows.welcome.steps[1]"
 * message, which is far more useful to a server owner than an exception.
 *
 * <p>{@code Material}, {@code EntityType} and {@code Particle} are plain
 * enums in both 1.21.4 and 26.1.2, so {@code valueOf} is safe and needs no
 * registry round-trip. {@code PotionEffectType} is not an enum, so it goes
 * through {@link Registry#MOB_EFFECT}, the modern key; the older
 * {@code Registry.EFFECT} alias and {@code PotionEffectType.getByName} are
 * both avoided as deprecated-for-removal.
 */
public final class Registries {

    private Registries() {
    }

    public static Material material(String name) {
        return enumValue(Material.class, name);
    }

    public static EntityType entityType(String name) {
        return enumValue(EntityType.class, name);
    }

    public static Particle particle(String name) {
        return enumValue(Particle.class, name);
    }

    /**
     * Resolves a potion effect by enum-style name ({@code JUMP_BOOST}) or
     * registry key ({@code minecraft:jump_boost}). Effect keys contain no
     * dots, so lowercasing the enum form yields the key directly, unlike
     * sounds, which need the inverted index in {@link Sounds}.
     */
    public static PotionEffectType potionEffect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String input = name.trim();
        NamespacedKey key = input.indexOf(':') >= 0
                ? NamespacedKey.fromString(input.toLowerCase(Locale.ROOT))
                : NamespacedKey.minecraft(input.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.MOB_EFFECT.get(key);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
