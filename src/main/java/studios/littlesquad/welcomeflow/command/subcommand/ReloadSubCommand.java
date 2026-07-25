package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.command.Permissions;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.config.ConfigManager;
import studios.littlesquad.welcomeflow.config.MessageService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reloads config.yml / flows.yml / messages.yml from disk.
 *
 * <p>M1 scope: config and message reload only. "Active sessions
 * preserved or gracefully restarted" (per the command table) applies
 * once FlowEngine/SessionManager exist in M2 -- this class already sits
 * in the one place that logic will be added.
 */
public final class ReloadSubCommand implements SubCommand {

    private final ConfigManager configManager;
    private final MessageService messages;
    private final Logger logger;

    public ReloadSubCommand(ConfigManager configManager, MessageService messages, Logger logger) {
        this.configManager = configManager;
        this.messages = messages;
        this.logger = logger;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public String usage() {
        return "reload";
    }

    @Override
    public String descriptionKey() {
        return "command.reload.description";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        try {
            configManager.reload();
            messages.reload();
            sender.sendMessage(messages.renderPrefixed("general.reload-success"));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Reload failed", e);
            sender.sendMessage(messages.renderPrefixed("general.reload-failed"));
        }
    }
}
