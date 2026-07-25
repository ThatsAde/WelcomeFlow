package studios.littlesquad.welcomeflow.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * One /wf subcommand. Implementations are registered with
 * {@link WelcomeFlowCommand} and are responsible for their own
 * permission check, argument validation and tab completion.
 */
public interface SubCommand {

    /** Name as typed after /wf, e.g. "start". */
    String name();

    /** Permission node required to run and to see in /wf help, or null for everyone. */
    String permission();

    /** Usage shown in /wf help, without the leading "/wf ", e.g. "start <player> [flow]". */
    String usage();

    /** messages.yml key for the one-line description shown in /wf help. */
    String descriptionKey();

    /** {@code args} excludes the subcommand name itself. */
    void execute(CommandSender sender, String[] args);

    /** {@code args} excludes the subcommand name itself. */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
