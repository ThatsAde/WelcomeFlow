package studios.littlesquad.welcomeflow.action.impl;

import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.action.ActionContext;
import studios.littlesquad.welcomeflow.action.ActionParseException;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.ActionRunner;
import studios.littlesquad.welcomeflow.action.ActionSpec;
import studios.littlesquad.welcomeflow.action.DelayAction;
import studios.littlesquad.welcomeflow.action.LambdaActionFactory;
import studios.littlesquad.welcomeflow.compat.CommandDispatch;
import studios.littlesquad.welcomeflow.compat.Registries;
import studios.littlesquad.welcomeflow.util.Durations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Registers {@code command}, {@code delay} and {@code conditional}.
 *
 * <p><strong>Scope note.</strong> {@code conditional} supports a fixed set
 * of conditions rather than a general expression language -- see the scope
 * judgements in PLAN.md. A parser with boolean operators and precedence
 * would be a multi-day feature and a permanent support burden; these five
 * cover the cases real configs need, and each one produces a precise
 * validation error when misspelled.
 */
public final class ControlActions {

    private ControlActions() {
    }

    /**
     * @param runnerSupplier the ActionRunner, supplied lazily because
     *     {@code conditional} needs the runner that is itself constructed
     *     alongside this registry -- a plain reference would be a
     *     chicken-and-egg at wiring time
     * @param nestedParser turns a nested YAML entry into an Action, so
     *     conditional branches support the same two spellings as top-level
     *     action lists
     */
    public static void registerInto(ActionRegistry registry,
                                    Supplier<ActionRunner> runnerSupplier,
                                    NestedActionParser nestedParser) {

        registry.register(new LambdaActionFactory(
                "command",
                "command: <cmd> | command[console]: <cmd>  (no leading slash; <player> is substituted)",
                spec -> {
                    String command = spec.requireString("command", 0);
                    String modifier = spec.modifier();
                    boolean asConsole = modifier == null || "console".equalsIgnoreCase(modifier);
                    if (modifier != null
                            && !"console".equalsIgnoreCase(modifier)
                            && !"player".equalsIgnoreCase(modifier)) {
                        throw new ActionParseException("Action 'command' at " + spec.configPath()
                                + " has unknown modifier '[" + modifier + "]'. Use [console] or [player].");
                    }
                    return ctx -> {
                        // Substituted textually rather than via MiniMessage: this
                        // is a command line, not a chat component.
                        String resolved = command
                                .replace("<player>", ctx.player().getName())
                                .replace("<flow>", ctx.flowId() == null ? "" : ctx.flowId())
                                .replace("<step>", ctx.stepId() == null ? "" : ctx.stepId());
                        String withoutSlash = resolved.startsWith("/") ? resolved.substring(1) : resolved;
                        if (asConsole) {
                            CommandDispatch.asConsole(withoutSlash);
                        } else {
                            CommandDispatch.asPlayer(ctx.player(), withoutSlash);
                        }
                    };
                }));

        registry.register(new LambdaActionFactory(
                "delay",
                "delay: <duration>  (ticks, or 20t / 3s / 2m)",
                spec -> {
                    String raw = spec.requireString("duration", 0);
                    long ticks = Durations.parseTicks(raw);
                    if (ticks < 0) {
                        throw new ActionParseException("Action 'delay' at " + spec.configPath()
                                + " could not read the duration '" + raw
                                + "'. Use ticks (40), or a suffix: 20t, 3s, 2m.");
                    }
                    return new DelayAction(ticks);
                }));

        registry.register(new LambdaActionFactory(
                "conditional",
                "conditional (map form): condition: <see docs>, then: [...], else: [...]",
                spec -> {
                    String condition = spec.requireString("condition", 0);
                    Predicate<ActionContext> test = parseCondition(condition, spec.configPath());
                    List<Action> thenBranch = nestedParser.parseAll(spec.list("then"), spec.configPath() + ".then");
                    List<Action> elseBranch = nestedParser.parseAll(spec.list("else"), spec.configPath() + ".else");
                    if (thenBranch.isEmpty() && elseBranch.isEmpty()) {
                        throw new ActionParseException("Action 'conditional' at " + spec.configPath()
                                + " has neither a 'then' nor an 'else' list, so it would do nothing.");
                    }
                    return new ConditionalAction(test, thenBranch, elseBranch, runnerSupplier);
                }));
    }

    /**
     * The supported conditions, each written as {@code kind:value}:
     * <ul>
     *   <li>{@code permission:some.node}</li>
     *   <li>{@code world:world_nether}</li>
     *   <li>{@code flow-completed:welcome}</li>
     *   <li>{@code has-item:DIAMOND} or {@code has-item:DIAMOND:3}</li>
     *   <li>{@code placeholder:%vault_eco_balance%=0} (equality, PAPI only)</li>
     * </ul>
     */
    private static Predicate<ActionContext> parseCondition(String raw, String configPath)
            throws ActionParseException {
        int colon = raw.indexOf(':');
        if (colon < 0) {
            throw new ActionParseException("Condition '" + raw + "' at " + configPath
                    + " must be written as kind:value, e.g. permission:my.node or world:world_nether.");
        }
        String kind = raw.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = raw.substring(colon + 1).trim();
        if (value.isEmpty()) {
            throw new ActionParseException("Condition '" + kind + "' at " + configPath + " has an empty value.");
        }

        return switch (kind) {
            case "permission" -> ctx -> ctx.player().hasPermission(value);
            case "world" -> ctx -> ctx.player().getWorld().getName().equalsIgnoreCase(value);
            case "flow-completed" -> ctx -> ctx.session().hasCompletedFlow(value);
            case "has-item" -> parseHasItem(value, configPath);
            case "placeholder" -> parsePlaceholderEquals(value, configPath);
            default -> throw new ActionParseException("Unknown condition kind '" + kind + "' at " + configPath
                    + ". Supported: permission, world, flow-completed, has-item, placeholder.");
        };
    }

    private static Predicate<ActionContext> parseHasItem(String value, String configPath)
            throws ActionParseException {
        String[] parts = value.split(":", 2);
        var material = Registries.material(parts[0]);
        if (material == null) {
            throw new ActionParseException("Condition 'has-item' at " + configPath
                    + " names an unknown material '" + parts[0] + "'.");
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new ActionParseException("Condition 'has-item' at " + configPath
                        + " expected a whole number amount but got '" + parts[1] + "'.");
            }
        }
        int required = Math.max(1, amount);
        return ctx -> ctx.player().getInventory().contains(material, required);
    }

    /**
     * {@code placeholder:%some_papi%=expected}. Resolution is left to the
     * PlaceholderAPI hook at execute time; without PAPI installed the
     * placeholder text is unchanged and the comparison simply fails, which is
     * the safe direction.
     */
    private static Predicate<ActionContext> parsePlaceholderEquals(String value, String configPath)
            throws ActionParseException {
        int equals = value.indexOf('=');
        if (equals < 0) {
            throw new ActionParseException("Condition 'placeholder' at " + configPath
                    + " must be written as placeholder:%some_placeholder%=expected-value.");
        }
        String placeholder = value.substring(0, equals).trim();
        String expected = value.substring(equals + 1).trim();
        return ctx -> {
            String resolved = studios.littlesquad.welcomeflow.integration.PlaceholderApiHook
                    .resolve(ctx.player(), placeholder);
            return expected.equalsIgnoreCase(resolved.trim());
        };
    }

    /** Turns nested YAML action entries into {@link Action}s. */
    @FunctionalInterface
    public interface NestedActionParser {
        List<Action> parseAll(List<Object> entries, String configPath) throws ActionParseException;
    }

    /**
     * Runs one of two action lists depending on a condition.
     *
     * <p>Delegates to the shared {@link ActionRunner} so nested lists get the
     * same non-blocking delay handling as top-level ones.
     */
    private static final class ConditionalAction implements Action {

        private final Predicate<ActionContext> condition;
        private final List<Action> thenBranch;
        private final List<Action> elseBranch;
        private final Supplier<ActionRunner> runnerSupplier;

        private ConditionalAction(Predicate<ActionContext> condition, List<Action> thenBranch,
                                  List<Action> elseBranch, Supplier<ActionRunner> runnerSupplier) {
            this.condition = condition;
            this.thenBranch = List.copyOf(thenBranch);
            this.elseBranch = List.copyOf(elseBranch);
            this.runnerSupplier = runnerSupplier;
        }

        @Override
        public void execute(ActionContext context) {
            List<Action> branch = condition.test(context) ? thenBranch : elseBranch;
            if (!branch.isEmpty()) {
                runnerSupplier.get().run(branch, context);
            }
        }
    }

    /**
     * Helper for registrars that need to turn a raw YAML list into specs.
     * Kept here because {@code conditional} is the only action with nested
     * lists.
     */
    public static List<ActionSpec> specsOf(List<Object> entries, String configPath) throws ActionParseException {
        List<ActionSpec> specs = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            Object entry = entries.get(index);
            String path = configPath + "[" + index + "]";
            if (entry instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                specs.add(ActionSpec.ofMap(typed, path));
            } else {
                specs.add(ActionSpec.ofString(String.valueOf(entry), path));
            }
        }
        return specs;
    }
}
