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
import studios.littlesquad.welcomeflow.flow.Step;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.List;

/**
 * {@code /wf status <player>}: current flow, step, progress and retention
 * eligibility.
 *
 * <p>Includes the retention explanations ("on join 1 of 2", "on cooldown for
 * another 4h 20m") because "why didn't my player get the reward?" is the
 * question this plugin will generate most support tickets about. Answering it
 * in-game means an owner never has to read a log.
 */
public final class StatusSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;

    public StatusSubCommand(WelcomeFlowPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
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
    public void execute(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(messages.renderPrefixed("general.player-not-found",
                        Text.placeholder("name", args[0])));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(messages.renderPrefixed("general.usage",
                    Text.placeholder("usage", usage())));
            return;
        }

        PlayerSession session = plugin.sessionManager().get(target.getUniqueId());
        if (session == null) {
            sender.sendMessage(messages.renderPrefixed("general.internal-error"));
            return;
        }

        sender.sendMessage(messages.render("command.status.header",
                Text.placeholder("target", target.getName())));

        if (!session.isInFlow()) {
            sender.sendMessage(messages.render("command.status.no-flow",
                    Text.placeholder("target", target.getName())));
        } else {
            sendFlowLines(sender, session);
        }

        sender.sendMessage(messages.render("command.status.line-joins",
                Text.placeholder("joins", String.valueOf(session.record().joinCount()))));
        sender.sendMessage(messages.render("command.status.line-second-join",
                Text.placeholder("state", plugin.secondJoinService().explain(session))));
        sender.sendMessage(messages.render("command.status.line-return-bonus",
                Text.placeholder("state", plugin.returnBonusService().explain(session))));
    }

    private void sendFlowLines(CommandSender sender, PlayerSession session) {
        Flow flow = plugin.flowRegistry().byId(session.progress().flowId());
        if (flow == null) {
            sender.sendMessage(messages.render("general.flow-not-found",
                    Text.placeholder("flow", session.progress().flowId())));
            return;
        }
        int index = session.progress().stepIndex();
        Step step = flow.stepAt(index);

        sender.sendMessage(messages.render("command.status.line-flow",
                Text.placeholder("flow", flow.id())));
        sender.sendMessage(messages.render("command.status.line-step",
                Text.placeholder("step", step == null ? "-" : step.id()),
                Text.placeholder("index", String.valueOf(index + 1)),
                Text.placeholder("total", String.valueOf(flow.stepCount()))));

        int required = step == null ? 0 : step.requiredAmount();
        int done = session.progress().stepProgress();
        int percent = required <= 0 ? 0 : Math.min(100, done * 100 / required);
        sender.sendMessage(messages.render("command.status.line-progress",
                Text.placeholder("percent", String.valueOf(percent)),
                Text.placeholder("progress", String.valueOf(done)),
                Text.placeholder("required", String.valueOf(required))));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabCompletions.onlinePlayerNames(args[0]) : List.of();
    }
}
