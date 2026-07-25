package studios.littlesquad.welcomeflow.compat;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Particle spawning via {@link ParticleBuilder}.
 *
 * <p>{@code Player#spawnParticle(...)} and {@code World#spawnParticle(...)}
 * lost every overload in paper-api 26.x. {@link ParticleBuilder} is a
 * regular class present -- with an identical method set -- in both
 * 1.21.4 and 26.1.2, so it is the only particle API used anywhere in this
 * plugin. Enforced by {@code compileVerify26Java} (see build.gradle.kts).
 */
public final class Particles {

    private Particles() {
    }

    public static void spawn(Player viewer, Particle particle, Location location, int count,
                              double offsetX, double offsetY, double offsetZ, double extra) {
        new ParticleBuilder(particle)
                .location(location)
                .receivers(viewer)
                .count(count)
                .offset(offsetX, offsetY, offsetZ)
                .extra(extra)
                .spawn();
    }

    public static void spawnToAll(Particle particle, Location location, int count,
                                   double offsetX, double offsetY, double offsetZ, double extra) {
        new ParticleBuilder(particle)
                .location(location)
                .allPlayers()
                .count(count)
                .offset(offsetX, offsetY, offsetZ)
                .extra(extra)
                .spawn();
    }
}
