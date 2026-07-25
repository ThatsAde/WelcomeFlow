package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;
import studios.littlesquad.welcomeflow.command.Permissions;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.config.ConfigIssue;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.List;

/**
 * {@code /wf validate} -- re-parses every config file and reports what is
 * wrong, with file, path and an actionable message.
 *
 * <p>This is a headline feature, so the output is held to a standard: every
 * line names the file and the exact YAML path, says what was expected, and
 * never shows a stacktrace. An owner should be able to fix the problem from
 * the message alone without opening the console.
 *
 * <p>Runs the real loader rather than a separate rule set, so what it reports
 * is exactly what the plugin would do -- a validator that drifts from the
 * parser is worse than none.
 */
public final class ValidateSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;

    public ValidateSubCommand(WelcomeFlowPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public String name() {
        return "validate";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public String usage() {
        return "validate";
    }

    @Override
    public String descriptionKey() {
        return "command.validate.description";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        List<ConfigIssue> issues = plugin.getConfigValidator().validateAll();

        if (issues.isEmpty()) {
            sender.sendMessage(messages.renderPrefixed("command.validate.ok"));
            return;
        }

        long errors = issues.stream().filter(ConfigIssue::isError).count();
        long warnings = issues.size() - errors;

        sender.sendMessage(messages.renderPrefixed("command.validate.header",
                Text.placeholder("count", String.valueOf(issues.size())),
                Text.placeholder("errors", String.valueOf(errors)),
                Text.placeholder("warnings", String.valueOf(warnings))));

        // Errors first: they are what actually breaks a flow.
        issues.stream().filter(ConfigIssue::isError).forEach(issue -> send(sender, issue));
        issues.stream().filter(issue -> !issue.isError()).forEach(issue -> send(sender, issue));
    }

    private void send(CommandSender sender, ConfigIssue issue) {
        sender.sendMessage(messages.render(
                issue.isError() ? "command.validate.line" : "command.validate.line-warning",
                Text.placeholder("file", issue.file()),
                Text.placeholder("path", issue.path()),
                Text.placeholder("message", issue.message())));
    }
}
