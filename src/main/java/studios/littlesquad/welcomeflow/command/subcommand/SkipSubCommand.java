package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;
import studios.littlesquad.welcomeflow.command.Permissions;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.command.TabCompletions;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.List;

/**
 * {@code /wf skip [player]}: force-completes the current step.
 *
 * <p>Defaults to the sender when no player is named, so an admin testing a
 * flow can walk through it quickly. The step's {@code on-step-complete}
 * actions still fire: skipping is for exercising the flow, not for bypassing
 * it invisibly.
 */
public final class SkipSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;

    public SkipSubCommand(WelcomeFlowPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public String name() {
        return "skip";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
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
            sender.sendMessage(messages.renderPrefixed("general.player-only"));
            return;
        }

        if (plugin.flowEngine().forceCompleteCurrentStep(target)) {
            sender.sendMessage(messages.renderPrefixed("command.skip.success",
                    Text.placeholder("target", target.getName())));
        } else {
            sender.sendMessage(messages.renderPrefixed("command.skip.no-active-step",
                    Text.placeholder("target", target.getName())));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabCompletions.onlinePlayerNames(args[0]) : List.of();
    }
}
