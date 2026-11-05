package studios.littlesquad.welcomeflow.action;

import java.util.Locale;

/**
 * The points in a flow's lifecycle that action lists can attach to.
 *
 * <p>The {@link #configKey()} is what owners write in flows.yml, so these
 * names are part of the public config contract; renaming one is a breaking
 * change for every existing config.
 */
public enum ActionTrigger {

    /** Flow assigned and begun, before the first step starts. */
    ON_FLOW_START,

    /** A step became the current one (also fires on resume). */
    ON_STEP_START,

    /** Progress was credited but the step is not finished yet. */
    ON_STEP_PROGRESS,

    /** A step's objective was satisfied. */
    ON_STEP_COMPLETE,

    /** The last step completed. */
    ON_FLOW_COMPLETE,

    /** The player dropped out and passed the abandon window. */
    ON_FLOW_ABANDON,

    /** The configured Nth login was reached. */
    ON_SECOND_JOIN,

    /** The player came back after being away long enough. */
    ON_RETURN_BONUS;

    private final String configKey = name().toLowerCase(Locale.ROOT).replace('_', '-');

    /** e.g. {@code "on-step-complete"} for {@link #ON_STEP_COMPLETE}. */
    public String configKey() {
        return configKey;
    }

    /** @return the matching trigger, or {@code null} if the key is unknown. */
    public static ActionTrigger fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        String normalised = key.trim().toLowerCase(Locale.ROOT);
        for (ActionTrigger trigger : values()) {
            if (trigger.configKey.equals(normalised)) {
                return trigger;
            }
        }
        return null;
    }
}
