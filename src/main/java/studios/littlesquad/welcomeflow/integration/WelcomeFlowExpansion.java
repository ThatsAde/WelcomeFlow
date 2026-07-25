package studios.littlesquad.welcomeflow.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.flow.Step;
import studios.littlesquad.welcomeflow.retention.ReturnBonusService;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.util.Durations;

import java.util.Locale;

/**
 * Exposes WelcomeFlow's state as PlaceholderAPI placeholders, for scoreboards,
 * tab lists and holograms.
 *
 * <p>All placeholders read the in-memory session, so they are cheap enough to
 * put on a scoreboard that refreshes every tick -- there is no database access
 * on any of these paths.
 *
 * <table>
 *   <caption>Available placeholders</caption>
 *   <tr><td>{@code %welcomeflow_active%}</td><td>true/false</td></tr>
 *   <tr><td>{@code %welcomeflow_flow%}</td><td>current flow id</td></tr>
 *   <tr><td>{@code %welcomeflow_step%}</td><td>current step's display text</td></tr>
 *   <tr><td>{@code %welcomeflow_step_index%}</td><td>1-based step number</td></tr>
 *   <tr><td>{@code %welcomeflow_steps_total%}</td><td>steps in the flow</td></tr>
 *   <tr><td>{@code %welcomeflow_progress_percent%}</td><td>0-100</td></tr>
 *   <tr><td>{@code %welcomeflow_progress_bar%}</td><td>rendered bar</td></tr>
 *   <tr><td>{@code %welcomeflow_completed%}</td><td>flows finished</td></tr>
 *   <tr><td>{@code %welcomeflow_joins%}</td><td>logins seen</td></tr>
 *   <tr><td>{@code %welcomeflow_next_return_bonus%}</td><td>time until eligible</td></tr>
 * </table>
 *
 * <p>Unknown placeholders return {@code null}, which tells PlaceholderAPI to
 * leave the text untouched rather than rendering an empty string -- that makes
 * a typo visible instead of silently blank.
 */
public final class WelcomeFlowExpansion extends PlaceholderExpansion {

    private static final int BAR_SEGMENTS = 10;

    private final WelcomeFlowPlugin plugin;
    private final ReturnBonusService returnBonusService;

    public WelcomeFlowExpansion(WelcomeFlowPlugin plugin, ReturnBonusService returnBonusService) {
        this.plugin = plugin;
        this.returnBonusService = returnBonusService;
    }

    @Override
    public String getIdentifier() {
        return "welcomeflow";
    }

    @Override
    public String getAuthor() {
        return "LittleSquad Studios";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * Keeps the expansion registered across PlaceholderAPI reloads.
     *
     * <p>Without this, {@code /papi reload} would unregister us and every
     * placeholder would silently stop resolving until the server restarted.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return null;
        }
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return null;
        }
        PlayerSession session = plugin.sessionManager().get(player.getUniqueId());
        if (session == null) {
            return null;
        }

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "active" -> String.valueOf(session.isInFlow());
            case "flow" -> session.isInFlow() ? session.progress().flowId() : "";
            case "step" -> currentStepDisplay(session);
            case "step_index" -> String.valueOf(stepIndex(session));
            case "steps_total" -> String.valueOf(stepsTotal(session));
            case "progress_percent" -> String.valueOf(progressPercent(session));
            case "progress_bar" -> progressBar(session);
            case "completed" -> String.valueOf(session.completedFlowIds().size());
            case "joins" -> String.valueOf(session.record().joinCount());
            case "next_return_bonus" -> nextReturnBonus(session);
            default -> null;
        };
    }

    private Flow currentFlow(PlayerSession session) {
        return session.isInFlow() ? plugin.flowRegistry().byId(session.progress().flowId()) : null;
    }

    private String currentStepDisplay(PlayerSession session) {
        Flow flow = currentFlow(session);
        if (flow == null) {
            return "";
        }
        Step step = flow.stepAt(session.progress().stepIndex());
        return step == null ? "" : step.display();
    }

    /** 1-based so it reads naturally as "step 3 of 6" on a scoreboard. */
    private int stepIndex(PlayerSession session) {
        return session.isInFlow() ? session.progress().stepIndex() + 1 : 0;
    }

    private int stepsTotal(PlayerSession session) {
        Flow flow = currentFlow(session);
        return flow == null ? 0 : flow.stepCount();
    }

    private int progressPercent(PlayerSession session) {
        Flow flow = currentFlow(session);
        if (flow == null || flow.stepCount() == 0) {
            return 0;
        }
        return Math.min(100, session.progress().stepIndex() * 100 / flow.stepCount());
    }

    /**
     * A plain-text bar such as {@code ■■■□□□□□□□}.
     *
     * <p>Deliberately uncoloured: PlaceholderAPI output lands in scoreboards
     * and holograms whose own colour handling varies, so the caller wraps it
     * in whatever formatting they use.
     */
    private String progressBar(PlayerSession session) {
        int filled = Math.round(progressPercent(session) / 100.0f * BAR_SEGMENTS);
        StringBuilder bar = new StringBuilder(BAR_SEGMENTS);
        for (int index = 0; index < BAR_SEGMENTS; index++) {
            bar.append(index < filled ? '■' : '□');
        }
        return bar.toString();
    }

    private String nextReturnBonus(PlayerSession session) {
        long remaining = returnBonusService.millisUntilEligible(session, System.currentTimeMillis());
        if (remaining < 0) {
            return "never";
        }
        return remaining == 0 ? "now" : Durations.humanize(remaining);
    }
}
