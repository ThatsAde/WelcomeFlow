package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.command.TabCompletions;
import studios.littlesquad.welcomeflow.config.MessageService;

import java.util.Collections;
import java.util.List;

/** {@code /wf skip [player]} -- completes the current step. Wired up in M2. */
public final class SkipSubCommand extends AbstractStubSubCommand {

    public SkipSubCommand(MessageService messages) {
        super(messages);
    }

    @Override
    public String name() {
        return "skip";
    }

    @Override
    public String usage() {
        return "skip [player]";
    }

    @Override
    public String descriptionKey() {
        return "command.skip.description";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabCompletions.onlinePlayerNames(args[0]) : Collections.emptyList();
    }
}
