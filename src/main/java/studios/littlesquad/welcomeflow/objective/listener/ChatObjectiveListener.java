package studios.littlesquad.welcomeflow.objective.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

/**
 * Handles {@link ObjectiveType#SEND_CHAT}.
 *
 * <p>Uses Paper's {@link AsyncChatEvent} rather than the deprecated
 * {@code AsyncPlayerChatEvent} -- the legacy event carries a String message
 * and is exactly the sort of API 26.x has been removing.
 *
 * <p><strong>This event fires off the main thread.</strong> Everything the
 * engine does -- session lookup, step advancement, running actions -- touches
 * Bukkit API and mutable session state, so the credit call is hopped back to
 * the main thread. Calling {@code creditProgress} directly from here would be
 * a data race against every other listener and a violation of the "never call
 * Bukkit API off the main thread" constraint.
 */
final class ChatObjectiveListener implements Listener {

    private final Plugin plugin;
    private final FlowEngine engine;

    ChatObjectiveListener(Plugin plugin, FlowEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        // Hop to the main thread; by the time it runs the player may have quit,
        // which the engine handles by finding no session.
        Bukkit.getScheduler().runTask(plugin,
                () -> engine.creditProgress(player, ObjectiveType.SEND_CHAT, 1));
    }
}
