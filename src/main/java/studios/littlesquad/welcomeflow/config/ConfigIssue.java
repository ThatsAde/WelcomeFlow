package studios.littlesquad.welcomeflow.config;

/**
 * One problem found while loading configuration.
 *
 * <p>Carries the file and the YAML path alongside the message because
 * "unknown material 'OAK_LOGG'" is far less useful than
 * "flows.yml @ flows.welcome.steps[1].objective.material: unknown material
 * 'OAK_LOGG'". Config validation being genuinely actionable is a selling
 * point of this plugin, not an afterthought.
 *
 * @param severity whether this breaks the flow or is merely suspicious
 * @param file the config file, e.g. {@code "flows.yml"}
 * @param path dotted YAML path to the offending key
 * @param message what is wrong and what was expected
 */
public record ConfigIssue(Severity severity, String file, String path, String message) {

    public enum Severity {
        /** The flow or action cannot work; it was skipped. */
        ERROR,
        /** Loaded fine, but almost certainly not what the owner intended. */
        WARNING
    }

    public static ConfigIssue error(String file, String path, String message) {
        return new ConfigIssue(Severity.ERROR, file, path, message);
    }

    public static ConfigIssue warning(String file, String path, String message) {
        return new ConfigIssue(Severity.WARNING, file, path, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** Single-line rendering used by console output and {@code /wf validate}. */
    public String describe() {
        return file + " @ " + path + ": " + message;
    }
}
