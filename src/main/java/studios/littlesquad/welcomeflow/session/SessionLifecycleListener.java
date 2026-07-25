package studios.littlesquad.welcomeflow.session;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import studios.littlesquad.welcomeflow.flow.FlowEngine;

import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives session load/unload from join and quit.
 *
 * <p>Both handlers are wrapped defensively: an exception escaping a join
 * handler can stop later plugins' handlers from running and, in the worst
 * case, leave a player in a broken state. Per the quality bar, nothing here
 * may leak a stacktrace during normal operation.
 */
public final class SessionLifecycleListener implements Listener {

    private final SessionManager sessionManager;
    private final FlowEngine flowEngine;
    private final Logger logger;

    /**
     * Extra work to run once a session is loaded, on the main thread --
     * the retention services (second-join, return bonus) hook in here in M3.
     */
    private final BiConsumer<org.bukkit.entity.Player, PlayerSession> onSessionReady;

    public SessionLifecycleListener(SessionManager sessionManager, FlowEngine flowEngine, Logger logger,
                                    BiConsumer<org.bukkit.entity.Player, PlayerSession> onSessionReady) {
        this.sessionManager = sessionManager;
        this.flowEngine = flowEngine;
        this.logger = logger;
        this.onSessionReady = onSessionReady;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        try {
            sessionManager.loadOnJoin(event.getPlayer(), session -> {
                try {
                    // Retention rewards are evaluated before flow assignment so
                    // a returning player's bonus lands before any tutorial text.
                    onSessionReady.accept(event.getPlayer(), session);
                    flowEngine.onJoin(event.getPlayer(), session);
                } catch (RuntimeException e) {
                    logger.log(Level.SEVERE, "Failed to start WelcomeFlow for "
                            + event.getPlayer().getName() + "; they will play without onboarding.", e);
                }
            });
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Failed to load WelcomeFlow session for "
                    + event.getPlayer().getName() + ".", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        try {
            PlayerSession session = sessionManager.get(event.getPlayer().getUniqueId());
            if (session != null) {
                // Record the drop-off step before the session is unloaded.
                flowEngine.onQuit(session);
            }
            sessionManager.unloadOnQuit(event.getPlayer().getUniqueId());
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Failed to save WelcomeFlow session for "
                    + event.getPlayer().getName() + " on quit.", e);
        }
    }
}
