package studios.littlesquad.welcomeflow.action;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.util.Text;

/**
 * Per-execution data handed to an {@link Action}.
 *
 * <p>Deliberately carries {@code flowId}/{@code stepId} as plain strings
 * rather than {@code Flow}/{@code Step} objects. Actions need identity and
 * progress numbers, not the whole definition, and keeping the types out
 * avoids a package cycle between {@code action} and {@code flow} -- the
 * flow model already depends on actions to hold its trigger lists.
 *
 * @param player the player the action runs for; never null (console-only
 *     actions like {@code broadcast} still run in a player's context)
 * @param session the player's live session
 * @param flowId id of the flow being run
 * @param stepId id of the current step, or {@code null} for flow-level
 *     triggers and the retention rewards
 * @param stepProgress units completed toward the current step
 * @param stepRequired units the current step needs, or {@code 0} when not
 *     applicable
 */
public record ActionContext(
        Player player,
        PlayerSession session,
        String flowId,
        String stepId,
        int stepProgress,
        int stepRequired) {

    /** Context for a flow-level trigger, with no current step. */
    public static ActionContext ofFlow(Player player, PlayerSession session, String flowId) {
        return new ActionContext(player, session, flowId, null, 0, 0);
    }

    /**
     * The MiniMessage placeholders every text-bearing action supports, so
     * {@code <player>}, {@code <progress>} and friends work consistently
     * across message/title/actionbar/broadcast without each action
     * reimplementing them.
     *
     * <p>Values are inserted as unparsed text, so a player with MiniMessage
     * syntax in their name cannot inject formatting.
     */
    public TagResolver[] placeholders() {
        int percent = stepRequired > 0
                ? Math.min(100, (int) Math.round(100.0 * stepProgress / stepRequired))
                : 0;
        return new TagResolver[] {
                Text.placeholder("player", player.getName()),
                Text.placeholder("flow", flowId == null ? "" : flowId),
                Text.placeholder("step", stepId == null ? "" : stepId),
                Text.placeholder("progress", String.valueOf(stepProgress)),
                Text.placeholder("required", String.valueOf(stepRequired)),
                Text.placeholder("percent", String.valueOf(percent)),
        };
    }

    /** Progress as a 0..1 fraction, for bossbars. */
    public float progressFraction() {
        if (stepRequired <= 0) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) stepProgress / stepRequired));
    }
}
