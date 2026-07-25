package studios.littlesquad.welcomeflow.command.subcommand;

import studios.littlesquad.welcomeflow.config.MessageService;

/** {@code /wf stats [flow]} -- retention funnel. Wired up in M3/M4. */
public final class StatsSubCommand extends AbstractStubSubCommand {

    public StatsSubCommand(MessageService messages) {
        super(messages);
    }

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public String usage() {
        return "stats [flow]";
    }

    @Override
    public String descriptionKey() {
        return "command.stats.description";
    }
}
