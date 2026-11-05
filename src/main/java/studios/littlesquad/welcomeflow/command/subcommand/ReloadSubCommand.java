package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;
import studios.littlesquad.welcomeflow.command.Permissions;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.config.ConfigIssue;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /wf reload}: re-reads every config file.
 *
 * <p>Active sessions are preserved: only the definitions are swapped, so a
 * player mid-flow keeps their step and progress. Objective listeners are
 * re-registered because the new flows.yml may use a different set of
 * objective types than the one it replaces.
 *
 * <p>Reports the same validation output as {@code /wf validate}, since a
 * reload that quietly loads a broken config is how servers end up with
 * onboarding that silently does nothing.
 */
public final class ReloadSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;
    private final Logger logger;

    public ReloadSubCommand(WelcomeFlowPlugin plugin, MessageService messages, Logger logger) {
        this.plugin = plugin;
        this.messages = messages;
        this.logger = logger;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public String usage() {
        return "reload";
    }

    @Override
    public String descriptionKey() {
        return "command.reload.description";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        try {
            List<ConfigIssue> issues = plugin.reloadEverything();
            sender.sendMessage(messages.renderPrefixed(issues.stream().anyMatch(ConfigIssue::isError)
                    ? "general.reload-failed" : "general.reload-success"));

            if (issues.isEmpty()) {
                return;
            }
            long errors = issues.stream().filter(ConfigIssue::isError).count();
            sender.sendMessage(messages.renderPrefixed("command.validate.header",
                    Text.placeholder("count", String.valueOf(issues.size())),
                    Text.placeholder("errors", String.valueOf(errors)),
                    Text.placeholder("warnings", String.valueOf(issues.size() - errors))));
            for (ConfigIssue issue : issues) {
                sender.sendMessage(messages.render(
                        issue.isError() ? "command.validate.line" : "command.validate.line-warning",
                        Text.placeholder("file", issue.file()),
                        Text.placeholder("path", issue.path()),
                        Text.placeholder("message", issue.message())));
            }
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Reload failed; the previous configuration is still active.", e);
            sender.sendMessage(messages.renderPrefixed("general.reload-failed"));
        }
    }
}
