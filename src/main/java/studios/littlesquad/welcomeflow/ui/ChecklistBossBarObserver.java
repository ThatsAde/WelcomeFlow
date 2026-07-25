package studios.littlesquad.welcomeflow.ui;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.flow.FlowObserver;
import studios.littlesquad.welcomeflow.flow.Step;
import studios.littlesquad.welcomeflow.session.PlayerSession;

/**
 * Keeps the checklist bossbar in sync with the engine's transitions.
 *
 * <p>A thin adapter on purpose: {@link ChecklistBossBarService} owns the bar
 * state and rendering, {@link studios.littlesquad.welcomeflow.flow.FlowEngine}
 * owns the transitions, and this class is the only thing that knows both
 * exist.
 */
public final class ChecklistBossBarObserver implements FlowObserver {

    private final ChecklistBossBarService bossBars;

    public ChecklistBossBarObserver(ChecklistBossBarService bossBars) {
        this.bossBars = bossBars;
    }

    @Override
    public void onStepStart(Player player, PlayerSession session, Flow flow, Step step,
                            int stepIndex, int progress) {
        // Also fires on resume after a relog, which is exactly when the bar
        // needs re-creating from scratch.
        bossBars.update(player, flow, step, stepIndex, progress, step.requiredAmount());
    }

    @Override
    public void onStepProgress(Player player, PlayerSession session, Flow flow, Step step,
                               int stepIndex, int progress) {
        bossBars.update(player, flow, step, stepIndex, progress, step.requiredAmount());
    }

    @Override
    public void onFlowComplete(Player player, PlayerSession session, Flow flow) {
        bossBars.hide(player);
    }

    @Override
    public void onFlowInterrupted(PlayerSession session, Flow flow, Step step) {
        // The player is already leaving, so there is nobody to hide the bar
        // from -- just drop our reference so the map does not grow.
        bossBars.forget(session.uuid());
    }
}
