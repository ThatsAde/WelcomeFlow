package studios.littlesquad.welcomeflow.action;

/**
 * Thrown by an {@link ActionFactory} when a config entry cannot be turned
 * into an action.
 *
 * <p>The message is shown verbatim to the server owner by
 * {@code /wf validate} and during startup validation, so it must name the
 * offending value and say what was expected, never "invalid input". See
 * the "config validation is a headline feature" requirement.
 */
public class ActionParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public ActionParseException(String message) {
        super(message);
    }

    public ActionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
