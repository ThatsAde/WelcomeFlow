package studios.littlesquad.welcomeflow.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dispatches /welcomeflow (aliases /wflow, /wf) to the registered
 * {@link SubCommand}s and drives their tab completion.
 *
 * <p>This is a Paper {@link BasicCommand} rather than a Bukkit
 * {@code CommandExecutor}: Paper plugins cannot declare commands in
 * {@code paper-plugin.yml}, so {@code JavaPlugin#getCommand} always throws
 * for us. Registration happens via {@code JavaPlugin#registerCommand} in
 * the plugin bootstrap. {@code BasicCommand} keeps the plain
 * {@code String[] args} shape the subcommands already expect, so we get
 * Brigadier registration without hand-building a command tree.
 *
 * <p>Both {@code BasicCommand} and the {@code registerCommand} overload
 * used here are byte-for-byte identical in paper-api 1.21.4 and 26.1.2,
 * so this compiles under the dual-target verification.
 */
public final class WelcomeFlowCommand implements BasicCommand {

    private final MessageService messages;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public WelcomeFlowCommand(MessageService messages, List<SubCommand> subCommandList) {
        this.messages = messages;
        for (SubCommand subCommand : subCommandList) {
            subCommands.put(subCommand.name().toLowerCase(Locale.ROOT), subCommand);
        }
    }

    /**
     * Gates the whole command on {@code welcomeflow.use}; individual
     * subcommands still check their own (admin) node in
     * {@link #execute(CommandSourceStack, String[])}.
     */
    @Override
    public String permission() {
        return Permissions.USE;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            sender.sendMessage(messages.renderPrefixed("general.unknown-subcommand"));
            return;
        }
        if (subCommand.permission() != null && !sender.hasPermission(subCommand.permission())) {
            sender.sendMessage(messages.renderPrefixed("general.no-permission"));
            return;
        }
        subCommand.execute(sender, tail(args));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        // No args at all, or still typing the subcommand name: suggest names.
        // Paper hands us an empty array for "/wf " and {"sta"} for "/wf sta".
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (SubCommand subCommand : subCommands.values()) {
                if (subCommand.permission() != null && !sender.hasPermission(subCommand.permission())) {
                    continue;
                }
                if (subCommand.name().startsWith(partial)) {
                    names.add(subCommand.name());
                }
            }
            Collections.sort(names);
            return names;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null
                || (subCommand.permission() != null && !sender.hasPermission(subCommand.permission()))) {
            return Collections.emptyList();
        }
        return subCommand.tabComplete(sender, tail(args));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messages.render("command.help.header"));
        for (SubCommand subCommand : subCommands.values()) {
            if (subCommand.permission() != null && !sender.hasPermission(subCommand.permission())) {
                continue;
            }
            sender.sendMessage(messages.render(
                    "command.help.line",
                    Text.placeholder("usage", subCommand.usage()),
                    Text.placeholder("description", messages.raw(subCommand.descriptionKey()))));
        }
    }

    private static String[] tail(String[] args) {
        return args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
    }
}
