package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#PICKUP_ITEM}.
 *
 * <p>{@code EntityPickupItemEvent} fires for mobs too (a zombie collecting
 * armour), hence the {@code Player} check before anything else.
 */
final class PickupObjectiveListener implements Listener {

    private final FlowEngine engine;

    PickupObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        // Credit the whole stack: picking up 12 logs should count as 12
        // toward a "collect 16 logs" step, not as one pickup.
        engine.creditProgress(
                player,
                ObjectiveType.PICKUP_ITEM,
                Math.max(1, stack.getAmount()),
                definition -> definition.matchesMaterial(stack.getType()));
    }
}
