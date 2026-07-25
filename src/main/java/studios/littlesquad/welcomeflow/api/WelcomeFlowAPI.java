package studios.littlesquad.welcomeflow.api;

import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;

/**
 * Public accessor for other plugins -- the one static singleton this
 * project's architecture allows (see PLAN.md). Session/flow methods
 * ({@code getSession}, {@code startFlow}, {@code completeObjective},
 * {@code isInFlow}) are added once FlowEngine and SessionManager exist
 * in M2; for now this only exposes plugin readiness so downstream code
 * can safely detect "is WelcomeFlow loaded and enabled".
 */
public final class WelcomeFlowAPI {

    private static volatile WelcomeFlowPlugin instance;

    private WelcomeFlowAPI() {
    }

    public static void register(WelcomeFlowPlugin plugin) {
        instance = plugin;
    }

    public static void unregister() {
        instance = null;
    }

    public static boolean isReady() {
        return instance != null;
    }

    /**
     * @throws IllegalStateException if WelcomeFlow has not finished
     *     enabling yet.
     */
    public static WelcomeFlowPlugin plugin() {
        WelcomeFlowPlugin current = instance;
        if (current == null) {
            throw new IllegalStateException("WelcomeFlowAPI accessed before the plugin finished enabling.");
        }
        return current;
    }
}
