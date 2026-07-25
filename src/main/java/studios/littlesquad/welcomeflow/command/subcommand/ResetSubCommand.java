package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.command.TabCompletions;
import studios.littlesquad.welcomeflow.config.MessageService;

import java.util.Collections;
import java.util.List;

/** {@code /wf reset <player>} -- wipes progress + reward flags. Wired up in M2/M3. */
public final class ResetSubCommand extends AbstractStubSubCommand {

    public ResetSubCommand(MessageService messages) {
        super(messages);
    }

    @Override
    public String name() {
        return "reset";
    }

    @Override
    public String usage() {
        return "reset <player>";
    }

    @Override
    public String descriptionKey() {
        return "command.reset.description";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabCompletions.onlinePlayerNames(args[0]) : Collections.emptyList();
    }
}
