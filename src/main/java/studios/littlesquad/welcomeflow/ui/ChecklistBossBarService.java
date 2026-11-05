package studios.littlesquad.welcomeflow.ui;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.config.ConfigManager;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.flow.Step;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The running checklist bossbar: which step the player is on, how far through
 * the flow they are, and progress within the current step.
 *
 * <p>Entirely event-driven. The bar is created when a flow starts, updated on
 * step transitions and progress credits, and removed on completion or logout.
 * There is no repeating task keeping it in sync, per the "no per-tick
 * scanning loops" constraint.
 *
 * <p>One bar per player, reused across steps rather than recreated: swapping
 * the name and progress on an existing bar avoids the visible flicker of
 * hiding one bar and showing another.
 */
public final class ChecklistBossBarService {

    private final ConfigManager config;
    private final MessageService messages;

    /** Live bars by player. Main thread only. */
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public ChecklistBossBarService(ConfigManager config, MessageService messages) {
        this.config = config;
        this.messages = messages;
    }

    /**
     * Shows or updates the checklist for the player's current position.
     *
     * <p>Respects both the global {@code ui.bossbar.enabled} toggle and the
     * flow's own {@code show-checklist-bossbar}; either can switch it off.
     */
    public void update(Player player, Flow flow, Step step, int stepIndex, int progress, int required) {
        if (!config.bossBarEnabled() || !flow.settings().showChecklistBossbar()) {
            hide(player);
            return;
        }

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(
                    renderName(flow, step, stepIndex, progress, required),
                    fraction(stepIndex, flow.stepCount()),
                    config.bossBarColor(),
                    config.bossBarStyle());
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
            return;
        }

        bar.name(renderName(flow, step, stepIndex, progress, required));
        bar.progress(fraction(stepIndex, flow.stepCount()));
        bar.color(config.bossBarColor());
        bar.overlay(config.bossBarStyle());
    }

    /** Removes the player's bar, if any. Safe to call when none exists. */
    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /** Forgets a player's bar without touching them, e.g. after they quit. */
    public void forget(UUID uuid) {
        bars.remove(uuid);
    }

    /**
     * Hides every bar. Called on disable and reload so bars do not linger on
     * screen after the plugin that owns them has stopped.
     */
    public void hideAll(Iterable<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
        bars.clear();
    }

    /**
     * Bar progress tracks position through the <em>flow</em> (steps done out
     * of total), not within the current step. Owners read the bar as "how far
     * through the tutorial am I"; per-step progress is in the label instead.
     */
    private static float fraction(int stepIndex, int stepCount) {
        if (stepCount <= 0) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) stepIndex / stepCount));
    }

    private net.kyori.adventure.text.Component renderName(Flow flow, Step step, int stepIndex,
                                                          int progress, int required) {
        // The label template lives in messages.yml so owners can restyle it
        // without touching flows, per "no hardcoded text anywhere in Java".
        return Text.parse(
                messages.raw("checklist.bossbar"),
                Text.placeholder("step", Text.parse(step.display())),
                Text.placeholder("step-number", String.valueOf(stepIndex + 1)),
                Text.placeholder("step-total", String.valueOf(flow.stepCount())),
                Text.placeholder("progress", String.valueOf(progress)),
                Text.placeholder("required", String.valueOf(required)),
                Text.placeholder("flow", flow.id()));
    }
}
