package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#WALK_DISTANCE} and
 * {@link ObjectiveType#REACH_LOCATION}, both driven by movement.
 *
 * <p>{@code PlayerMoveEvent} is by far the most frequent event this plugin
 * touches -- it fires for head rotation too -- so the handler is written to
 * bail out as early and as cheaply as possible:
 * <ol>
 *   <li>{@code ignoreCancelled} keeps us out of cancelled moves entirely.</li>
 *   <li>A same-block check discards look-only and sub-block movement before
 *       any session lookup.</li>
 *   <li>Only then does it reach the engine, which drops the event again if
 *       the player's current step is not a movement objective.</li>
 * </ol>
 *
 * <p>{@link EventPriority#MONITOR} because we only observe; we never cancel
 * or modify movement.
 */
final class MovementObjectiveListener implements Listener {

    /** Counter key for the fractional block accumulator in the session. */
    private static final String WALK_COUNTER = "walk-distance";

    private final FlowEngine engine;

    MovementObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Head movement fires this event with identical coordinates; skipping
        // it here avoids a map lookup per mouse movement per player.
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        creditWalking(event, from, to);
        checkReachedLocation(event, to);
    }

    private void creditWalking(PlayerMoveEvent event, Location from, Location to) {
        var session = engine.sessionFor(event.getPlayer());
        if (session == null) {
            return;
        }
        // Fractions are banked in the session so a player crossing a block
        // boundary in several small moves still gets credit for exactly one.
        double travelled = from.distance(to);
        int wholeBlocks = session.addAndDrainWholeUnits(WALK_COUNTER, travelled);
        if (wholeBlocks > 0) {
            engine.creditProgress(event.getPlayer(), ObjectiveType.WALK_DISTANCE, wholeBlocks);
        }
    }

    private void checkReachedLocation(PlayerMoveEvent event, Location to) {
        engine.creditProgress(event.getPlayer(), ObjectiveType.REACH_LOCATION, 1, definition -> {
            Location target = engine.namedLocation(definition.locationName());
            if (target == null || target.getWorld() == null || !target.getWorld().equals(to.getWorld())) {
                return false;
            }
            double radius = definition.radius();
            // distanceSquared avoids a sqrt on every qualifying move.
            return to.distanceSquared(target) <= radius * radius;
        });
    }
}
