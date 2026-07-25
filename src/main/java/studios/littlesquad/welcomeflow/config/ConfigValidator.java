package studios.littlesquad.welcomeflow.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates config.yml / flows.yml / messages.yml and reports every
 * problem found, with file + path + an actionable message -- never a
 * raw stacktrace. This is a headline feature (see PLAN.md / /wf validate)
 * whose full rule set (unknown keys, invalid materials/sounds/enums,
 * unreachable steps, duplicate step ids, invalid MiniMessage, missing
 * soft-dependencies) is implemented in M4. This class exists now so
 * callers (startup, /wf validate) have a stable shape to code against.
 */
public final class ConfigValidator {

    public record Problem(String file, String path, String message) {
    }

    private final ConfigManager configManager;

    public ConfigValidator(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Runs every validation rule and returns the problems found. An
     * empty list means the configuration is safe to use.
     */
    public List<Problem> validateAll() {
        List<Problem> problems = new ArrayList<>();
        // TODO(M4): unknown keys, invalid materials/sounds/enums,
        // unreachable steps, duplicate step ids, invalid MiniMessage,
        // missing soft-dependencies. configManager is already wired in
        // so this method just needs its body filled in.
        return Collections.unmodifiableList(problems);
    }
}
