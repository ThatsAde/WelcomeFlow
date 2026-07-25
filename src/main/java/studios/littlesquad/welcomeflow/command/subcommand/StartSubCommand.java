package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;
import studios.littlesquad.welcomeflow.command.Permissions;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.command.TabCompletions;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.List;

/**
 * {@code /wf start <player> [flow]} -- force-starts a flow, ignoring its
 * conditions.
 *
 * <p>With no flow named, runs the normal selection logic instead, which is
 * the quick way for an owner to check what a new player would actually be
 * given without creating a test account.
 */
public final class StartSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;

    public StartSubCommand(WelcomeFlowPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public String name() {
        return "start";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
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
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.renderPrefixed("general.usage", Text.placeholder("usage", usage())));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.renderPrefixed("general.player-not-found",
                    Text.placeholder("name", args[0])));
            return;
        }

        PlayerSession session = plugin.sessionManager().get(target.getUniqueId());
        if (session == null) {
            sender.sendMessage(messages.renderPrefixed("general.internal-error"));
            return;
        }
        if (session.isInFlow()) {
            sender.sendMessage(messages.renderPrefixed("command.start.already-in-flow",
                    Text.placeholder("target", target.getName())));
            return;
        }

        Flow flow = args.length >= 2
                ? plugin.flowRegistry().byId(args[1])
                : plugin.flowRegistry().selectFor(target, session);

        if (flow == null) {
            sender.sendMessage(messages.renderPrefixed("general.flow-not-found",
                    Text.placeholder("flow", args.length >= 2 ? args[1] : "(auto)")));
            return;
        }

        plugin.flowEngine().startFlow(target, session, flow);
        sender.sendMessage(messages.renderPrefixed("command.start.success",
                Text.placeholder("flow", flow.id()),
                Text.placeholder("target", target.getName())));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabCompletions.onlinePlayerNames(args[0]);
        }
        if (args.length == 2) {
            return TabCompletions.matching(plugin.flowRegistry().flowIds(), args[1]);
        }
        return List.of();
    }
}
