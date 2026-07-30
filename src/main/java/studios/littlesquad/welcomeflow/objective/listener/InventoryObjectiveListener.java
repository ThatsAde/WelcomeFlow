package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#OPEN_INVENTORY}.
 *
 * <p>This type needs two events, because Minecraft only reports half of it.
 * Opening a container (chest, furnace, crafting table, a plugin GUI) sends a
 * packet and fires {@link InventoryOpenEvent}. Opening your <em>own</em>
 * inventory with E does not: the client draws that screen locally and the
 * server never hears about it, so an open-only listener leaves a player who
 * was told "open your inventory" stuck on the step forever.
 *
 * <p>Closing it, however, does send a packet. So the player's own inventory is
 * credited on {@link InventoryCloseEvent} instead, guarded on
 * {@link InventoryType#CRAFTING} -- the view type of the default player screen.
 * A container is credited on open and skipped on close, so nothing is counted
 * twice.
 *
 * <p>The close is additionally required to be {@code Reason.PLAYER}: a death,
 * teleport or disconnect also closes the screen, and crediting those would
 * advance the step without the player having done anything.
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

    /** Credits the player's own inventory, which has no open event. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getType() != InventoryType.CRAFTING) {
            return; // a container; already credited when it opened
        }
        if (event.getReason() != InventoryCloseEvent.Reason.PLAYER) {
            return; // death, teleport, disconnect: the player did not do this
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        engine.creditProgress(player, ObjectiveType.OPEN_INVENTORY, 1);
    }
}
