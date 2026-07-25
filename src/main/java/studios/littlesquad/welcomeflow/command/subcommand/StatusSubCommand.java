package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.command.TabCompletions;
import studios.littlesquad.welcomeflow.config.MessageService;

import java.util.Collections;
import java.util.List;

/** {@code /wf status <player>} -- shows current flow/step/progress. Wired up in M2. */
public final class StatusSubCommand extends AbstractStubSubCommand {

    public StatusSubCommand(MessageService messages) {
        super(messages);
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String usage() {
        return "status <player>";
    }

    @Override
    public String descriptionKey() {
        return "command.status.description";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabCompletions.onlinePlayerNames(args[0]) : Collections.emptyList();
    }
}
