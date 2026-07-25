package studios.littlesquad.welcomeflow.flow;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.action.ActionTrigger;
import studios.littlesquad.welcomeflow.session.PlayerSession;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A complete onboarding flow: ordered steps plus lifecycle actions and the
 * conditions that decide who gets it.
 *
 * @param id key under {@code flows:} in flows.yml
 * @param priority higher wins when several flows match; ties break on id so
 *     selection is deterministic
 * @param conditions who is eligible
 * @param settings per-flow behaviour toggles
 * @param steps ordered; an empty list makes the flow unassignable
 * @param actions flow-level action lists by trigger
 */
public record Flow(
        String id,
        int priority,
        Conditions conditions,
        Settings settings,
        List<Step> steps,
        Map<ActionTrigger, List<Action>> actions) {

    public Flow {
        steps = List.copyOf(steps);
        actions = Map.copyOf(actions);
    }

    /**
     * Eligibility rules, all of which must pass.
     *
     * @param firstJoinOnly restrict to a player's very first login
     * @param permission required node, or empty for everyone
     * @param worlds allowed world names, or empty for all
     * @param previousFlowCompleted flow that must already be finished, or
     *     {@code null} for no prerequisite -- this is what lets flows chain
     */
    public record Conditions(
            boolean firstJoinOnly,
            String permission,
            Set<String> worlds,
            String previousFlowCompleted) {

        public Conditions {
            worlds = Set.copyOf(worlds);
        }

        public static Conditions permissive() {
            return new Conditions(false, "", Set.of(), null);
        }

        /**
         * Whether this player may be assigned the owning flow right now.
         *
         * <p>World names are compared case-insensitively because owners
         * routinely write {@code World} for a folder called {@code world}.
         */
        public boolean matches(Player player, PlayerSession session) {
            if (firstJoinOnly && !session.record().isFirstJoin()) {
                return false;
            }
            if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                return false;
            }
            if (!worlds.isEmpty()) {
                String current = player.getWorld().getName().toLowerCase(Locale.ROOT);
                boolean allowed = worlds.stream()
                        .anyMatch(world -> world.toLowerCase(Locale.ROOT).equals(current));
                if (!allowed) {
                    return false;
                }
            }
            return previousFlowCompleted == null
                    || previousFlowCompleted.isEmpty()
                    || session.hasCompletedFlow(previousFlowCompleted);
        }

        /** Human-readable reason a player was skipped, for debug logging. */
        public String explainMismatch(Player player, PlayerSession session) {
            if (firstJoinOnly && !session.record().isFirstJoin()) {
                return "first-join-only and this is join #" + session.record().joinCount();
            }
            if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                return "missing permission '" + permission + "'";
            }
            if (!worlds.isEmpty()) {
                String current = player.getWorld().getName().toLowerCase(Locale.ROOT);
                boolean allowed = worlds.stream()
                        .anyMatch(world -> world.toLowerCase(Locale.ROOT).equals(current));
                if (!allowed) {
                    return "world '" + player.getWorld().getName() + "' is not in the allowed list";
                }
            }
            if (previousFlowCompleted != null && !previousFlowCompleted.isEmpty()
                    && !session.hasCompletedFlow(previousFlowCompleted)) {
                return "prerequisite flow '" + previousFlowCompleted + "' not completed";
            }
            return "conditions matched";
        }
    }

    /**
     * @param resumeOnRejoin pick the flow back up where it stopped; when
     *     false, an interrupted run restarts from step 0
     * @param abandonAfterHours how long an interrupted run stays resumable
     *     before being counted as abandoned; {@code 0} means never abandon
     * @param showChecklistBossbar show the running checklist bossbar
     */
    public record Settings(
            boolean resumeOnRejoin,
            int abandonAfterHours,
            boolean showChecklistBossbar) {

        public static Settings defaults() {
            return new Settings(true, 48, true);
        }

        public long abandonAfterMillis() {
            return abandonAfterHours <= 0 ? 0L : TimeUnit.HOURS.toMillis(abandonAfterHours);
        }
    }

    public List<Action> actionsFor(ActionTrigger trigger) {
        return actions.getOrDefault(trigger, List.of());
    }

    public int stepCount() {
        return steps.size();
    }

    /** @return the step at {@code index}, or {@code null} when out of range. */
    public Step stepAt(int index) {
        return index >= 0 && index < steps.size() ? steps.get(index) : null;
    }

    /** @return index of the step with {@code stepId}, or {@code -1}. */
    public int indexOfStep(String stepId) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equalsIgnoreCase(stepId)) {
                return index;
            }
        }
        return -1;
    }

    /** True when {@code index} is past the last step. */
    public boolean isFinished(int index) {
        return index >= steps.size();
    }
}
