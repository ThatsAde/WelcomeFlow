package studios.littlesquad.welcomeflow.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps an action's config {@code type} to the factory that builds it.
 *
 * <p>Registration happens once at startup from the registrar classes in
 * {@code action.impl}, each of which owns a cohesive group of action types
 * and carries the services those types need.
 *
 * <p>Lookup is by {@link ActionSpec#baseType()}, so {@code command} and
 * {@code command[console]} resolve to the same factory and the modifier is
 * interpreted there.
 */
public final class ActionRegistry {

    private final Map<String, ActionFactory> factories = new LinkedHashMap<>();

    public void register(ActionFactory factory) {
        String type = factory.type().toLowerCase(Locale.ROOT);
        ActionFactory previous = factories.put(type, factory);
        if (previous != null) {
            throw new IllegalStateException("Two factories registered for action type '" + type + "'.");
        }
    }

    /**
     * Builds the action described by {@code spec}.
     *
     * @throws ActionParseException if the type is unknown or its fields are
     *     invalid; the message names the config path and lists the known
     *     types, so {@code /wf validate} output is directly actionable
     */
    public Action create(ActionSpec spec) throws ActionParseException {
        ActionFactory factory = factories.get(spec.baseType());
        if (factory == null) {
            throw new ActionParseException("Unknown action type '" + spec.baseType() + "' at "
                    + spec.configPath() + ". Known types: " + String.join(", ", knownTypes()) + ".");
        }
        return factory.create(spec);
    }

    public boolean isKnown(String baseType) {
        return baseType != null && factories.containsKey(baseType.toLowerCase(Locale.ROOT));
    }

    public List<String> knownTypes() {
        return List.copyOf(factories.keySet());
    }

    /** Usage lines for every registered type, for docs and validation help. */
    public List<String> usageLines() {
        List<String> lines = new ArrayList<>(factories.size());
        factories.values().forEach(factory -> lines.add(factory.usage()));
        return lines;
    }

    public int size() {
        return factories.size();
    }
}
