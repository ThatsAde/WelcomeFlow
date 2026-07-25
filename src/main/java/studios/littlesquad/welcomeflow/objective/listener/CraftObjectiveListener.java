package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#CRAFT_ITEM}.
 *
 * <p>Counting crafted items correctly is fiddlier than it looks. A single
 * craft click can produce one stack, and a shift-click can produce many. We
 * deliberately credit only the recipe's own result amount per click rather
 * than trying to compute the shift-click multiplier: over-counting a "craft 1
 * crafting table" step would complete it from a single shift-click of planks,
 * which is arguably correct, but under-counting would leave a player stuck.
 * Crediting the result amount is the honest, predictable middle -- and for the
 * common {@code amount: 1} step the distinction never arises.
 */
final class CraftObjectiveListener implements Listener {

    private final FlowEngine engine;

    CraftObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        // One click credits one recipe output. A shift-click may actually
        // produce several, but the real count depends on the inputs available
        // and is not knowable here without simulating the inventory.
        int crafted = Math.max(1, result.getAmount());

        engine.creditProgress(
                player,
                ObjectiveType.CRAFT_ITEM,
                crafted,
                definition -> definition.matchesMaterial(result.getType()));
    }
}
