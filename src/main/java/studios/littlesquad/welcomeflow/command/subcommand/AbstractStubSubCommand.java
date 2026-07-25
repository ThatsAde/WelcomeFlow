package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.command.Permissions;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.config.MessageService;

/**
 * Base for admin subcommands whose real implementation depends on
 * pieces built in later milestones (FlowEngine in M2, retention/stats
 * services in M3/M4). Handles the shared permission node and the
 * "not wired up yet" reply so each concrete class only states its
 * name/usage/description/tab-completion shape; {@link #execute} is
 * overridden once the milestone that backs it lands.
 */
public abstract class AbstractStubSubCommand implements SubCommand {

    protected final MessageService messages;

    protected AbstractStubSubCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(messages.renderPrefixed("general.coming-soon"));
    }
}
