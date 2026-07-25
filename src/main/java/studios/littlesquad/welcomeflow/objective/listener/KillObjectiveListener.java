package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#KILL_ENTITY}.
 *
 * <p>Credits the killer, not the victim, and only when the killer is a
 * player -- {@code getKiller()} is null for environmental deaths (fall, lava,
 * mob-on-mob), which must not tick anyone's step.
 */
final class KillObjectiveListener implements Listener {

    private final FlowEngine engine;

    KillObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        engine.creditProgress(
                killer,
                ObjectiveType.KILL_ENTITY,
                1,
                definition -> definition.matchesEntity(victim.getType()));
    }
}
