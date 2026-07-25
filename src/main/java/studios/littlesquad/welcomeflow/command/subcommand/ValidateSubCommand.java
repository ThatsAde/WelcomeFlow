package studios.littlesquad.welcomeflow.command.subcommand;

import studios.littlesquad.welcomeflow.config.MessageService;

/** {@code /wf validate} -- dry-run config check. Wired up in M4. */
public final class ValidateSubCommand extends AbstractStubSubCommand {

    public ValidateSubCommand(MessageService messages) {
        super(messages);
    }

    @Override
    public String name() {
        return "validate";
    }

    @Override
    public String usage() {
        return "validate";
    }

    @Override
    public String descriptionKey() {
        return "command.validate.description";
    }
}
