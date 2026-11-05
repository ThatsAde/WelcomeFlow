package studios.littlesquad.welcomeflow.command.subcommand;

import org.bukkit.command.CommandSender;
import studios.littlesquad.welcomeflow.WelcomeFlowPlugin;
import studios.littlesquad.welcomeflow.command.Permissions;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.command.TabCompletions;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.flow.Step;
import studios.littlesquad.welcomeflow.storage.FunnelEvent;
import studios.littlesquad.welcomeflow.storage.StorageException;
import studios.littlesquad.welcomeflow.util.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /wf stats [flow]}: the retention funnel.
 *
 * <p>Renders joined -> started -> per-step reached -> completed, with a
 * conversion percentage against the top of the funnel and a drop-off count
 * per step. The drop-off column is the actionable part: it tells an owner
 * <em>which step</em> is losing their new players, which is the whole reason
 * the counters exist.
 *
 * <p>Reads the database, so it goes async after acknowledging, per the
 * "answers in under a tick or goes async with a working message" rule.
 */
public final class StatsSubCommand implements SubCommand {

    private final WelcomeFlowPlugin plugin;
    private final MessageService messages;

    public StatsSubCommand(WelcomeFlowPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public String usage() {
        return "stats [flow]";
    }

    @Override
    public String descriptionKey() {
        return "command.stats.description";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Flow flow;
        if (args.length >= 1) {
            flow = plugin.flowRegistry().byId(args[0]);
            if (flow == null) {
                sender.sendMessage(messages.renderPrefixed("general.flow-not-found",
                        Text.placeholder("flow", args[0])));
                return;
            }
        } else {
            List<Flow> all = plugin.flowRegistry().all();
            if (all.isEmpty()) {
                sender.sendMessage(messages.renderPrefixed("command.stats.no-flows"));
                return;
            }
            flow = all.get(0);
        }

        final Flow target = flow;
        sender.sendMessage(messages.renderPrefixed("general.working"));

        plugin.scheduling().supplyAsync(
                () -> {
                    try {
                        Map<String, Long> counts = new LinkedHashMap<>(
                                plugin.storage().funnelCounts(target.id()));
                        counts.putAll(plugin.storage().funnelCounts(FunnelEvent.GLOBAL_SCOPE));
                        return counts;
                    } catch (StorageException e) {
                        plugin.getLogger().warning("Could not read stats: " + e.getMessage());
                        return null;
                    }
                },
                counts -> {
                    if (counts == null) {
                        sender.sendMessage(messages.renderPrefixed("general.internal-error"));
                        return;
                    }
                    render(sender, target, counts);
                });
    }

    private void render(CommandSender sender, Flow flow, Map<String, Long> counts) {
        long joined = counts.getOrDefault(FunnelEvent.JOINED.key(), 0L);
        long started = counts.getOrDefault(FunnelEvent.STARTED.key(), 0L);
        long completed = counts.getOrDefault(FunnelEvent.COMPLETED.key(), 0L);

        sender.sendMessage(messages.render("command.stats.header", Text.placeholder("flow", flow.id())));
        line(sender, "Joined", joined, joined);
        line(sender, "Started", started, joined);

        for (Step step : flow.steps()) {
            long reached = counts.getOrDefault(FunnelEvent.stepReachedKey(step.id()), 0L);
            long droppedHere = counts.getOrDefault(FunnelEvent.stepAbandonedKey(step.id()), 0L);
            sender.sendMessage(messages.render("command.stats.step-line",
                    Text.placeholder("step", step.id()),
                    Text.placeholder("count", String.valueOf(reached)),
                    Text.placeholder("percent", percentOf(reached, joined)),
                    Text.placeholder("dropped", String.valueOf(droppedHere))));
        }

        line(sender, "Completed", completed, joined);

        // Retention counters live under the reserved (global) scope because
        // they are not tied to any one flow.
        sender.sendMessage(messages.render("command.stats.retention-header"));
        line(sender, "Second-join rewards",
                counts.getOrDefault("second-join", 0L), joined);
        line(sender, "Return bonuses",
                counts.getOrDefault("return-bonus", 0L), joined);
    }

    private void line(CommandSender sender, String label, long count, long total) {
        sender.sendMessage(messages.render("command.stats.line",
                Text.placeholder("label", label),
                Text.placeholder("count", String.valueOf(count)),
                Text.placeholder("percent", percentOf(count, total))));
    }

    /** Conversion against the top of the funnel; "-" when there is no baseline. */
    private static String percentOf(long count, long total) {
        if (total <= 0) {
            return "-";
        }
        return Math.round(100.0 * count / total) + "%";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1
                ? TabCompletions.matching(plugin.flowRegistry().flowIds(), args[0])
                : List.of();
    }
}
