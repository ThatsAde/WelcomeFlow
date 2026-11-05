package studios.littlesquad.welcomeflow.action.impl;

import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import studios.littlesquad.welcomeflow.action.ActionParseException;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.LambdaActionFactory;
import studios.littlesquad.welcomeflow.compat.Particles;
import studios.littlesquad.welcomeflow.compat.Registries;
import studios.littlesquad.welcomeflow.compat.Sounds;

/**
 * Registers {@code sound}, {@code particle} and {@code potion-effect}.
 *
 * <p>Every one of these goes through the {@code compat} facade rather than
 * the Bukkit API directly, because all three underlying calls
 * ({@code Player#playSound}, {@code spawnParticle}) were removed in
 * paper-api 26.x.
 *
 * <p>Sound names are resolved lazily at execute time, not at parse time: the
 * sound registry is not populated while the plugin is still enabling, so
 * resolving during config load would fail for every sound.
 */
public final class EffectActions {

    private EffectActions() {
    }

    public static void registerInto(ActionRegistry registry) {
        registry.register(new LambdaActionFactory(
                "sound",
                "sound: <SOUND_NAME>;;<volume>;;<pitch>",
                spec -> {
                    String soundName = spec.requireString("sound", 0);
                    float volume = (float) spec.decimal("volume", 1, 1.0);
                    float pitch = (float) spec.decimal("pitch", 2, 1.0);
                    return ctx -> {
                        NamespacedKey key = Sounds.resolve(soundName);
                        if (key == null) {
                            return; // Reported by ConfigValidator, not worth spamming per play.
                        }
                        Sounds.play(ctx.player(), key, Sound.Source.MASTER, volume, pitch);
                    };
                }));

        registry.register(new LambdaActionFactory(
                "particle",
                "particle: <PARTICLE>;;<count>;;<offsetX>;;<offsetY>;;<offsetZ>;;<extra>",
                spec -> {
                    String particleName = spec.requireString("particle", 0);
                    Particle particle = Registries.particle(particleName);
                    if (particle == null) {
                        throw new ActionParseException("Unknown particle '" + particleName + "' at "
                                + spec.configPath() + ". Use a Bukkit Particle name, e.g. HAPPY_VILLAGER.");
                    }
                    int count = spec.integer("count", 1, 10);
                    double offsetX = spec.decimal("offsetX", 2, 0.5);
                    double offsetY = spec.decimal("offsetY", 3, 1.0);
                    double offsetZ = spec.decimal("offsetZ", 4, 0.5);
                    double extra = spec.decimal("extra", 5, 0.0);
                    return ctx -> Particles.spawn(
                            ctx.player(), particle, ctx.player().getLocation(),
                            count, offsetX, offsetY, offsetZ, extra);
                }));

        registry.register(new LambdaActionFactory(
                "potion-effect",
                "potion-effect: <EFFECT>;;<seconds>;;<amplifier>",
                spec -> {
                    String effectName = spec.requireString("effect", 0);
                    PotionEffectType effectType = Registries.potionEffect(effectName);
                    if (effectType == null) {
                        throw new ActionParseException("Unknown potion effect '" + effectName + "' at "
                                + spec.configPath() + ". Use a name like SPEED or JUMP_BOOST.");
                    }
                    int seconds = spec.integer("seconds", 1, 10);
                    int amplifier = spec.integer("amplifier", 2, 0);
                    // Bukkit counts effect duration in ticks.
                    PotionEffect effect = new PotionEffect(effectType, Math.max(1, seconds) * 20, amplifier);
                    return ctx -> ctx.player().addPotionEffect(effect);
                }));
    }
}
