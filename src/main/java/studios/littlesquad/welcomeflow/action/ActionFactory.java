package studios.littlesquad.welcomeflow.action;

/**
 * Builds one kind of {@link Action} from its config entry.
 *
 * <p>Factories are registered once in {@link ActionRegistry} with whatever
 * services they need already injected, so the {@link Action} instances they
 * produce are self-contained and hold no service lookups.
 *
 * <p>All validation belongs here rather than in {@link Action#execute}:
 * parsing happens at load time and on {@code /wf validate}, which is what
 * lets configuration errors be reported with a file and path before a player
 * ever triggers them.
 */
public interface ActionFactory {

    /**
     * The {@code type} this factory handles, lowercase and without any
     * bracketed modifier, e.g. {@code "command"} handles both
     * {@code command:} and {@code command[console]:}.
     */
    String type();

    /** One-line description of the expected syntax, shown in validation errors. */
    String usage();

    Action create(ActionSpec spec) throws ActionParseException;
}
