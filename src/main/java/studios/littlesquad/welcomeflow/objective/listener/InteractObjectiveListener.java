package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#INTERACT_BLOCK}.
 *
 * <p>Two filters matter here and both prevent double-counting:
 * <ul>
 *   <li>Only {@link Action#RIGHT_CLICK_BLOCK} counts -- left-clicking is
 *       block-breaking, which BREAK_BLOCK already covers.</li>
 *   <li>Only the main hand counts. {@code PlayerInteractEvent} fires twice for
 *       a single right-click (once per hand), so without this a "click the
 *       crafting table" step with {@code amount: 2} would complete in one
 *       click.</li>
 * </ul>
 */
final class InteractObjectiveListener implements Listener {

    private final FlowEngine engine;

    InteractObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        engine.creditProgress(
                event.getPlayer(),
                ObjectiveType.INTERACT_BLOCK,
                1,
                definition -> definition.matchesMaterial(block.getType()));
    }
}
