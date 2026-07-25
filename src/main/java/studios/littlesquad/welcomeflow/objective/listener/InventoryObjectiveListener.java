package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#OPEN_INVENTORY}.
 *
 * <p>Note that {@code InventoryOpenEvent} does <em>not</em> fire when a player
 * opens their own survival inventory (the client handles that locally, no
 * packet reaches the server). It fires for chests, crafting tables, furnaces,
 * shop GUIs and so on. The bundled flow's "open your inventory" step therefore
 * completes when the player opens any container, which is the useful
 * behaviour -- and is documented as such in flows.yml so owners are not
 * surprised.
 */
final class InventoryObjectiveListener implements Listener {

    private final FlowEngine engine;

    InventoryObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        engine.creditProgress(player, ObjectiveType.OPEN_INVENTORY, 1);
    }
}
