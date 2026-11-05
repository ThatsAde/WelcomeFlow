package studios.littlesquad.welcomeflow.objective.listener;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Registers objective listeners on demand and unregisters them on reload.
 *
 * <p>Only the types a loaded flow actually uses get a listener, per the
 * "do not create listeners for objective types that are unused by any loaded
 * flow" requirement. On a server whose only flow is walk-and-break, this
 * registers two handlers instead of eleven, so the events the plugin does
 * not care about cost literally nothing, not even a no-op call.
 *
 * <p>{@link #registerFor} is safe to call repeatedly: it tears down the
 * previous set first, which is what makes {@code /wf reload} able to pick up
 * a flows.yml that started or stopped using a given objective type.
 */
public final class ObjectiveListeners {

    private final Plugin plugin;
    private final FlowEngine engine;
    private final Logger logger;

    /** Factories per type, so nothing is instantiated until it is needed. */
    private final Map<ObjectiveType, Supplier<Listener>> factories = new EnumMap<>(ObjectiveType.class);

    private final List<Listener> active = new ArrayList<>();

    public ObjectiveListeners(Plugin plugin, FlowEngine engine, Logger logger) {
        this.plugin = plugin;
        this.engine = engine;
        this.logger = logger;
        registerFactories();
    }

    private void registerFactories() {
        // WALK_DISTANCE and REACH_LOCATION share PlayerMoveEvent, the highest
        // frequency event we touch, so they intentionally map to one listener
        // that is registered if either type is in use.
        Supplier<Listener> movement = () -> new MovementObjectiveListener(engine);
        factories.put(ObjectiveType.WALK_DISTANCE, movement);
        factories.put(ObjectiveType.REACH_LOCATION, movement);

        factories.put(ObjectiveType.BREAK_BLOCK, () -> new BlockObjectiveListener(engine));
        factories.put(ObjectiveType.PLACE_BLOCK, () -> new BlockObjectiveListener(engine));
        factories.put(ObjectiveType.INTERACT_BLOCK, () -> new InteractObjectiveListener(engine));
        factories.put(ObjectiveType.CRAFT_ITEM, () -> new CraftObjectiveListener(engine));
        factories.put(ObjectiveType.PICKUP_ITEM, () -> new PickupObjectiveListener(engine));
        factories.put(ObjectiveType.KILL_ENTITY, () -> new KillObjectiveListener(engine));
        factories.put(ObjectiveType.RUN_COMMAND, () -> new CommandObjectiveListener(engine));
        factories.put(ObjectiveType.SEND_CHAT, () -> new ChatObjectiveListener(plugin, engine));
        factories.put(ObjectiveType.OPEN_INVENTORY, () -> new InventoryObjectiveListener(engine));
        // MANUAL and PLAY_TIME are listenerless by design; see
        // ObjectiveType.listenerless().
    }

    /**
     * Registers exactly the listeners needed for {@code typesInUse}.
     *
     * <p>Deduplicates by listener class, so the block listener covering both
     * BREAK_BLOCK and PLACE_BLOCK is registered once even when a flow uses
     * both.
     */
    public void registerFor(Set<ObjectiveType> typesInUse) {
        unregisterAll();

        Set<Class<? extends Listener>> alreadyRegistered = new java.util.HashSet<>();
        List<String> registeredFor = new ArrayList<>();

        for (ObjectiveType type : typesInUse) {
            Supplier<Listener> factory = factories.get(type);
            if (factory == null) {
                continue; // listenerless type
            }
            Listener listener = factory.get();
            if (!alreadyRegistered.add(listener.getClass())) {
                continue;
            }
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            active.add(listener);
            registeredFor.add(listener.getClass().getSimpleName());
        }

        logger.info("Objective listeners active: "
                + (registeredFor.isEmpty() ? "none (no flows use event-driven objectives)"
                        : String.join(", ", registeredFor)));
    }

    /** Unregisters every listener this class registered. */
    public void unregisterAll() {
        for (Listener listener : active) {
            HandlerList.unregisterAll(listener);
        }
        active.clear();
    }

    public int activeCount() {
        return active.size();
    }
}
