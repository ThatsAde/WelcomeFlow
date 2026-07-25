package studios.littlesquad.welcomeflow.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.flow.FlowObserver;
import studios.littlesquad.welcomeflow.flow.Step;
import studios.littlesquad.welcomeflow.session.PlayerSession;

/**
 * Republishes engine transitions as Bukkit events for other plugins.
 *
 * <p>Registered as just another {@link FlowObserver}, exactly like the
 * checklist bossbar -- which is why adding the public API in M4 required no
 * changes to {@code FlowEngine}'s transition logic.
 *
 * <p>{@code FlowEngine} guards observer calls, so a third-party handler that
 * throws is logged and cannot derail the player's flow.
 */
public final class EventPublishingObserver implements FlowObserver {

    /**
     * Fires the cancellable {@link FlowStartEvent}.
     *
     * <p>This is the veto point, called by the engine before it writes
     * anything -- which is what makes the event's {@code setCancelled(true)}
     * actually prevent the flow rather than merely being ignored.
     */
    @Override
    public boolean allowFlowStart(Player player, PlayerSession session, Flow flow, boolean resumed) {
        FlowStartEvent event = new FlowStartEvent(player, flow.id(), flow.stepCount(), resumed);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public void onStepStart(Player player, PlayerSession session, Flow flow, Step step,
                            int stepIndex, int progress) {
        Bukkit.getPluginManager().callEvent(new StepStartEvent(
                player, flow.id(), step.id(), stepIndex, flow.stepCount(),
                progress, step.requiredAmount()));
    }

    @Override
    public void onStepComplete(Player player, PlayerSession session, Flow flow, Step step) {
        int stepIndex = flow.indexOfStep(step.id());
        Bukkit.getPluginManager().callEvent(new StepCompleteEvent(
                player, flow.id(), step.id(), stepIndex, flow.stepCount(), false));
    }

    @Override
    public void onFlowComplete(Player player, PlayerSession session, Flow flow) {
        long startedAt = session.progress() == null ? 0L : session.progress().startedAt();
        Bukkit.getPluginManager().callEvent(new FlowCompleteEvent(
                player, flow.id(), flow.stepCount(), startedAt, System.currentTimeMillis()));
    }

    @Override
    public void onFlowInterrupted(PlayerSession session, Flow flow, Step step) {
        Bukkit.getPluginManager().callEvent(new FlowAbandonEvent(
                Bukkit.getOfflinePlayer(session.uuid()),
                flow.id(),
                step == null ? null : step.id(),
                session.progress() == null ? 0 : session.progress().stepIndex(),
                flow.stepCount()));
    }
}
