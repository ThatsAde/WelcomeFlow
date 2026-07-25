package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#RUN_COMMAND}.
 *
 * <p>Listens on {@code PlayerCommandPreprocessEvent}, which fires <em>before</em>
 * the command executes. That is deliberate: waiting for successful execution
 * would mean a step never completes if the target command is missing or the
 * player lacks permission, leaving them stuck with no explanation.
 *
 * <p>Matching is delegated to
 * {@link studios.littlesquad.welcomeflow.objective.ObjectiveDefinition#matchesCommand},
 * which normalises case, the leading slash and repeated whitespace, and
 * supports a trailing {@code *} wildcard. Without that normalisation
 * {@code /Kit Starter} would silently fail to satisfy a
 * {@code pattern: "/kit starter"} step -- the exact class of bug that
 * generates support tickets nobody can reproduce.
 *
 * <p>Note this does not resolve command <em>aliases</em>: a server where
 * {@code /kit} is an alias of {@code /essentials:kit} still needs the pattern
 * to match what the player types, which is what players are told to type
 * anyway.
 */
final class CommandObjectiveListener implements Listener {

    private final FlowEngine engine;

    CommandObjectiveListener(FlowEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        engine.creditProgress(
                event.getPlayer(),
                ObjectiveType.RUN_COMMAND,
                1,
                definition -> definition.matchesCommand(message));
    }
}
