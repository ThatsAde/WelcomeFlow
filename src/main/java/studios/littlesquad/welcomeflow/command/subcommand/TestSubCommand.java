package studios.littlesquad.welcomeflow.command.subcommand;

import studios.littlesquad.welcomeflow.config.MessageService;

/** {@code /wf test <flow>} -- runs a flow on yourself without persisting. Wired up in M2. */
public final class TestSubCommand extends AbstractStubSubCommand {

    public TestSubCommand(MessageService messages) {
        super(messages);
    }

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String usage() {
        return "test <flow>";
    }

    @Override
    public String descriptionKey() {
        return "command.test.description";
    }
}
