package studios.littlesquad.welcomeflow.action;

/**
 * An {@link ActionFactory} whose parsing step is supplied as a lambda.
 *
 * <p>Most action types need one short parse function and nothing else, so a
 * dedicated class per type would be fourteen files of ceremony. The
 * registrars in {@code action.impl} use this instead and keep each action's
 * parsing and behaviour adjacent and readable.
 */
public record LambdaActionFactory(String type, String usage, Parser parser) implements ActionFactory {

    /** The parse step: config entry in, ready-to-run action out. */
    @FunctionalInterface
    public interface Parser {
        Action parse(ActionSpec spec) throws ActionParseException;
    }

    @Override
    public Action create(ActionSpec spec) throws ActionParseException {
        return parser.parse(spec);
    }
}
