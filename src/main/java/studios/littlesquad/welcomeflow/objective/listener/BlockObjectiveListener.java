package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#BREAK_BLOCK} and
 * {@link ObjectiveType#PLACE_BLOCK}.
 *
 * <p>Both use {@code ignoreCancelled = true} so protected regions do not
 * count: a player clicking a WorldGuard-protected block should not tick the
 * "break 5 logs" step. {@link EventPriority#MONITOR} guarantees we see the
 * final decision of every protection plugin rather than racing them.
 */
final class BlockObjectiveListener implements Listener {

    private final FlowEngine engine;

    BlockObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        engine.creditProgress(
                event.getPlayer(),
                ObjectiveType.BREAK_BLOCK,
                1,
                definition -> definition.matchesMaterial(event.getBlock().getType()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        engine.creditProgress(
                event.getPlayer(),
                ObjectiveType.PLACE_BLOCK,
                1,
                definition -> definition.matchesMaterial(event.getBlock().getType()));
    }
}
