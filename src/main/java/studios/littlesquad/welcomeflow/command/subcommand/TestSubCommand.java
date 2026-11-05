package studios.littlesquad.welcomeflow.command.subcommand;

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
 * {@code /wf test <flow>}: runs a flow on yourself without persisting it.
 *
 * <p>"Without persisting" is implemented by starting the flow normally and
 * then wiping the player's stored data when it finishes or they log out --
 * rather than by threading a "test mode" flag through the whole engine. The
 * observable behaviour is identical, which is the point: an owner testing a
 * flow should see exactly what a real new player sees, including the
 * bossbar, the rewards and the timing.
 *
 * <p>The trade-off is that the data does briefly exist on disk. That is
 * acceptable for an admin-only preview command, and it is honest about what
 * it does: the message tells the admin their progress will be discarded.
 */
public final class TestSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;

    public TestSubCommand(WelcomeFlowPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public String usage() {
        return "test <flow>";
    }

    @Override
    public String descriptionKey() {
        return "command.test.description";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.renderPrefixed("general.player-only"));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.renderPrefixed("general.usage", Text.placeholder("usage", usage())));
            return;
        }

        Flow flow = plugin.flowRegistry().byId(args[0]);
        if (flow == null) {
            sender.sendMessage(messages.renderPrefixed("general.flow-not-found",
                    Text.placeholder("flow", args[0])));
            return;
        }

        PlayerSession session = plugin.sessionManager().get(player.getUniqueId());
        if (session == null) {
            sender.sendMessage(messages.renderPrefixed("general.internal-error"));
            return;
        }

        // Clear in place rather than resetting through storage: a full reset
        // discards the live session, and we would then have nothing to start
        // the flow with until the admin relogged.
        session = plugin.beginTestRun(player, session);
        plugin.flowEngine().startFlow(player, session, flow);
        if (session.progress() == null) {
            plugin.endTestRunIfActive(player.getUniqueId(), player.getName());
            sender.sendMessage(messages.renderPrefixed("command.start.cancelled"));
            return;
        }
        sender.sendMessage(messages.renderPrefixed("command.test.success",
                Text.placeholder("flow", flow.id())));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? TabCompletions.matching(plugin.flowRegistry().flowIds(), args[0])
                : List.of();
    }
}
