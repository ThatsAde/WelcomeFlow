package studios.littlesquad.welcomeflow.action.impl;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.LambdaActionFactory;
import studios.littlesquad.welcomeflow.config.ConfigManager;
import studios.littlesquad.welcomeflow.util.Scheduling;
import studios.littlesquad.welcomeflow.util.Text;

import java.time.Duration;
import java.util.Locale;

/**
 * Registers the text-presentation actions: {@code message}, {@code title},
 * {@code actionbar}, {@code broadcast} and {@code bossbar}.
 *
 * <p>Everything goes through Adventure. Notably {@code sendActionBar} is
 * called with a {@link Component} rather than the {@code String} overloads
 * that also exist; those are legacy-text APIs and are exactly the kind of
 * thing 26.x has been pruning.
 */
public final class TextActions {

    private TextActions() {
    }

    public static void registerInto(ActionRegistry registry, ConfigManager config, Scheduling scheduling) {
        registry.register(new LambdaActionFactory(
                "message",
                "message: <text>",
                spec -> {
                    String text = spec.requireString("text", 0);
                    return ctx -> ctx.player().sendMessage(Text.parse(text, ctx.placeholders()));
                }));

        registry.register(new LambdaActionFactory(
                "broadcast",
                "broadcast: <text>",
                spec -> {
                    String text = spec.requireString("text", 0);
                    return ctx -> {
                        Component rendered = Text.parse(text, ctx.placeholders());
                        Bukkit.getServer().sendMessage(rendered);
                    };
                }));

        registry.register(new LambdaActionFactory(
                "actionbar",
                "actionbar: <text>",
                spec -> {
                    String text = spec.requireString("text", 0);
                    return ctx -> ctx.player().sendActionBar(Text.parse(text, ctx.placeholders()));
                }));

        registry.register(new LambdaActionFactory(
                "title",
                "title: <text>;;<subtitle>;;<fade-in>;;<stay>;;<fade-out>  (ticks optional)",
                spec -> {
                    String text = spec.requireString("text", 0);
                    String subtitle = spec.string("subtitle", 1, "");
                    // Unspecified timings fall back to the ui.title defaults in
                    // config.yml, so owners set house style once.
                    int fadeIn = spec.integer("fade-in", 2, config.titleDefaultFadeInTicks());
                    int stay = spec.integer("stay", 3, config.titleDefaultStayTicks());
                    int fadeOut = spec.integer("fade-out", 4, config.titleDefaultFadeOutTicks());
                    return ctx -> ctx.player().showTitle(Title.title(
                            Text.parse(text, ctx.placeholders()),
                            Text.parse(subtitle, ctx.placeholders()),
                            Title.Times.times(
                                    ticks(fadeIn),
                                    ticks(stay),
                                    ticks(fadeOut))));
                }));

        registry.register(new LambdaActionFactory(
                "bossbar",
                "bossbar: <text>;;<seconds>;;<color>;;<overlay>;;<progress-mode: full|step>",
                spec -> {
                    String text = spec.requireString("text", 0);
                    double seconds = spec.decimal("seconds", 1, 5.0);
                    BossBar.Color color = enumOrDefault(
                            BossBar.Color.class, spec.string("color", 2), config.bossBarColor());
                    BossBar.Overlay overlay = enumOrDefault(
                            BossBar.Overlay.class, spec.string("overlay", 3), config.bossBarStyle());
                    boolean trackStepProgress = "step".equalsIgnoreCase(
                            spec.string("progress-mode", 4, "full"));
                    return new TemporaryBossBarAction(
                            text, seconds, color, overlay, trackStepProgress, scheduling);
                }));
    }

    private static Duration ticks(int count) {
        return Duration.ofMillis(Math.max(0L, count) * 50L);
    }

    /**
     * Enum lookup that degrades to a configured default instead of throwing,
     * so a typo in a bossbar colour costs a default-coloured bar rather than
     * a broken flow. ConfigValidator reports the typo separately.
     */
    private static <E extends Enum<E>> E enumOrDefault(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Shows a bossbar for a fixed duration, then hides it.
     *
     * <p>A dedicated class rather than a lambda because it needs to schedule
     * its own teardown: the bar must be hidden even though the action list
     * has already moved on.
     */
    private static final class TemporaryBossBarAction implements Action {

        private final String text;
        private final double seconds;
        private final BossBar.Color color;
        private final BossBar.Overlay overlay;
        private final boolean trackStepProgress;
        private final Scheduling scheduling;

        private TemporaryBossBarAction(String text, double seconds, BossBar.Color color, BossBar.Overlay overlay,
                                       boolean trackStepProgress, Scheduling scheduling) {
            this.text = text;
            this.seconds = seconds;
            this.color = color;
            this.overlay = overlay;
            this.trackStepProgress = trackStepProgress;
            this.scheduling = scheduling;
        }

        @Override
        public void execute(studios.littlesquad.welcomeflow.action.ActionContext context) {
            float progress = trackStepProgress ? context.progressFraction() : 1.0f;
            BossBar bar = BossBar.bossBar(
                    Text.parse(text, context.placeholders()), progress, color, overlay);
            context.player().showBossBar(bar);

            long hideAfterTicks = Math.max(1L, Math.round(seconds * 20.0));
            scheduling.runSyncLater(() -> context.player().hideBossBar(bar), hideAfterTicks);
        }
    }
}
