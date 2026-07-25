package studios.littlesquad.welcomeflow.api;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.session.PlayerSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point for other plugins.
 *
 * <p>This is the one static singleton in WelcomeFlow; everything behind it is
 * constructor-injected. Every method here is <strong>main-thread only</strong>
 * -- they read live session state that the engine mutates on the main thread,
 * so calling them from an async task is a data race.
 *
 * <h2>Getting started</h2>
 * Add WelcomeFlow as a soft-dependency in your {@code plugin.yml} /
 * {@code paper-plugin.yml}, then guard your calls:
 *
 * <pre>{@code
 * if (WelcomeFlowAPI.isReady()) {
 *     WelcomeFlowAPI.getActiveFlowId(player).ifPresent(flowId ->
 *             getLogger().info(player.getName() + " is in flow " + flowId));
 * }
 * }</pre>
 *
 * <h2>Reacting to progress</h2>
 * Prefer the events in {@code studios.littlesquad.welcomeflow.api.event} over
 * polling this class:
 * {@link studios.littlesquad.welcomeflow.api.event.FlowStartEvent},
 * {@link studios.littlesquad.welcomeflow.api.event.StepStartEvent},
 * {@link studios.littlesquad.welcomeflow.api.event.StepCompleteEvent},
 * {@link studios.littlesquad.welcomeflow.api.event.FlowCompleteEvent},
 * {@link studios.littlesquad.welcomeflow.api.event.FlowAbandonEvent} and
 * {@link studios.littlesquad.welcomeflow.api.event.RetentionRewardEvent}.
 *
 * <h2>Driving a MANUAL objective</h2>
 * A step with {@code type: MANUAL} advances only when you say so, which is
 * how you integrate a tutorial step with your own plugin's milestone:
 *
 * <pre>{@code
 * // "the player opened my custom shop GUI"
 * WelcomeFlowAPI.completeCurrentStep(player);
 * }</pre>
 */
public final class WelcomeFlowAPI {

    private static volatile WelcomeFlowPlugin instance;

    private WelcomeFlowAPI() {
    }

    /** Called by the plugin on enable. Not part of the public contract. */
    public static void register(WelcomeFlowPlugin plugin) {
        instance = plugin;
    }

    /** Called by the plugin on disable. Not part of the public contract. */
    public static void unregister() {
        instance = null;
    }

    /**
     * Whether WelcomeFlow has finished enabling.
     *
     * <p>Always check this first: WelcomeFlow is usually an optional
     * dependency, and plugin load order is not guaranteed.
     */
    public static boolean isReady() {
        return instance != null;
    }

    /**
     * @throws IllegalStateException if WelcomeFlow is not enabled; call
     *     {@link #isReady()} first
     */
    public static WelcomeFlowPlugin plugin() {
        WelcomeFlowPlugin current = instance;
        if (current == null) {
            throw new IllegalStateException("WelcomeFlowAPI accessed before the plugin finished enabling.");
        }
        return current;
    }

    // --- session queries --------------------------------------------------

    /**
     * The player's live session, present only while they are online.
     *
     * <p>Prefer the narrower helpers below unless you genuinely need the
     * session object -- they are stable across internal refactors.
     */
    public static Optional<PlayerSession> getSession(UUID uuid) {
        return isReady()
                ? Optional.ofNullable(plugin().sessionManager().get(uuid))
                : Optional.empty();
    }

    /** True when the player is currently part-way through a flow. */
    public static boolean isInFlow(Player player) {
        return getSession(player.getUniqueId())
                .map(PlayerSession::isInFlow)
                .orElse(false);
    }

    /** The id of the flow the player is in, if any. */
    public static Optional<String> getActiveFlowId(Player player) {
        return getSession(player.getUniqueId())
                .filter(PlayerSession::isInFlow)
                .map(session -> session.progress().flowId());
    }

    /** The id of the step the player is currently on, if any. */
    public static Optional<String> getActiveStepId(Player player) {
        return getSession(player.getUniqueId())
                .filter(PlayerSession::isInFlow)
                .flatMap(session -> {
                    Flow flow = plugin().flowRegistry().byId(session.progress().flowId());
                    if (flow == null) {
                        return Optional.empty();
                    }
                    var step = flow.stepAt(session.progress().stepIndex());
                    return step == null ? Optional.<String>empty() : Optional.of(step.id());
                });
    }

    /**
     * How far through their flow the player is, as a percentage.
     *
     * @return 0-100, or empty when they are not in a flow
     */
    public static Optional<Integer> getProgressPercent(Player player) {
        return getSession(player.getUniqueId())
                .filter(PlayerSession::isInFlow)
                .flatMap(session -> {
                    Flow flow = plugin().flowRegistry().byId(session.progress().flowId());
                    if (flow == null || flow.stepCount() == 0) {
                        return Optional.empty();
                    }
                    int done = session.progress().stepIndex();
                    return Optional.of(Math.min(100, done * 100 / flow.stepCount()));
                });
    }

    /**
     * Whether the player has finished a given flow.
     *
     * <p>Reads the in-memory session, so it only answers for online players;
     * offline lookups would need a database round-trip and are deliberately
     * not offered on the main thread.
     */
    public static boolean hasCompletedFlow(Player player, String flowId) {
        return getSession(player.getUniqueId())
                .map(session -> session.hasCompletedFlow(flowId))
                .orElse(false);
    }

    // --- actions ----------------------------------------------------------

    /**
     * Starts a flow for a player, ignoring its conditions.
     *
     * <p>Does nothing if they are already in a flow -- reset them first with
     * {@link #resetPlayer(Player)} if you mean to replace it.
     *
     * @param flowId id from flows.yml
     * @return true if the flow was started
     */
    public static boolean startFlow(Player player, String flowId) {
        if (!isReady()) {
            return false;
        }
        return plugin().startFlowFor(player, flowId);
    }

    /**
     * Completes the player's current step regardless of its objective.
     *
     * <p>This is how a {@code MANUAL} step is advanced, and it works for any
     * other type too.
     *
     * @return true if a step was completed
     */
    public static boolean completeCurrentStep(Player player) {
        return isReady() && plugin().flowEngine().forceCompleteCurrentStep(player);
    }

    /**
     * Wipes a player's progress and reward claim flags.
     *
     * <p>Storage work happens asynchronously; the in-memory session is
     * cleared immediately.
     */
    public static void resetPlayer(Player player) {
        if (isReady()) {
            plugin().resetPlayerData(player.getUniqueId(), player.getName());
        }
    }

    // --- configuration ----------------------------------------------------

    /** Ids of every loaded flow, highest priority first. */
    public static List<String> getFlowIds() {
        return isReady()
                ? plugin().flowRegistry().all().stream().map(Flow::id).toList()
                : List.of();
    }
}
