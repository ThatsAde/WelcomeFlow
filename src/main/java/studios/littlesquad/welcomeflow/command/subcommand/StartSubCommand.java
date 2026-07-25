package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.command.TabCompletions;
import studios.littlesquad.welcomeflow.config.MessageService;

import java.util.Collections;
import java.util.List;

/** {@code /wf start <player> [flow]} -- force-starts a flow. Wired up in M2. */
public final class StartSubCommand extends AbstractStubSubCommand {

    public StartSubCommand(MessageService messages) {
        super(messages);
    }

    @Override
    public String name() {
        return "start";
    }

    @Override
    public String usage() {
        return "start <player> [flow]";
    }

    @Override
    public String descriptionKey() {
        return "command.start.description";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabCompletions.onlinePlayerNames(args[0]) : Collections.emptyList();
    }
}
