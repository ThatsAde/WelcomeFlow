package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 * {@code /wf reset <player>}: wipes progress and reward claim flags.
 *
 * <p>Works for offline players too, which matters because the usual reason to
 * reset someone is to let them re-run the tutorial <em>before</em> they next
 * log in. Resolving an offline profile can touch disk, so that path
 * acknowledges with "working..." and answers asynchronously rather than
 * stalling the server.
 */
public final class ResetSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;

    public ResetSubCommand(WelcomeFlowPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public String name() {
        return "reset";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
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
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.renderPrefixed("general.usage", Text.placeholder("usage", usage())));
            return;
        }

        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            plugin.resetPlayerData(online.getUniqueId(), online.getName());
            sender.sendMessage(messages.renderPrefixed("command.reset.success",
                    Text.placeholder("target", online.getName())));
            return;
        }

        sender.sendMessage(messages.renderPrefixed("general.working"));
        plugin.scheduling().supplyAsync(
                () -> Bukkit.getOfflinePlayer(name),
                profile -> {
                    if (profile == null || !profile.hasPlayedBefore()) {
                        sender.sendMessage(messages.renderPrefixed("general.player-not-found",
                                Text.placeholder("name", name)));
                        return;
                    }
                    String resolved = displayName(profile, name);
                    plugin.resetPlayerData(profile.getUniqueId(), resolved);
                    sender.sendMessage(messages.renderPrefixed("command.reset.success",
                            Text.placeholder("target", resolved)));
                });
    }

    private static String displayName(OfflinePlayer profile, String fallback) {
        return profile.getName() == null ? fallback : profile.getName();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabCompletions.onlinePlayerNames(args[0]) : List.of();
    }
}
