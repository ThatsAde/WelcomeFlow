package studios.littlesquad.welcomeflow;

import org.bukkit.plugin.java.JavaPlugin;
import studios.littlesquad.welcomeflow.action.Action;
import studios.littlesquad.welcomeflow.action.ActionParseException;
import studios.littlesquad.welcomeflow.action.ActionRegistry;
import studios.littlesquad.welcomeflow.action.ActionRunner;
import studios.littlesquad.welcomeflow.action.impl.ControlActions;
import studios.littlesquad.welcomeflow.action.impl.EffectActions;
import studios.littlesquad.welcomeflow.action.impl.TextActions;
import studios.littlesquad.welcomeflow.action.impl.WorldActions;
import studios.littlesquad.welcomeflow.api.WelcomeFlowAPI;
import studios.littlesquad.welcomeflow.command.SubCommand;
import studios.littlesquad.welcomeflow.command.WelcomeFlowCommand;
import studios.littlesquad.welcomeflow.command.subcommand.ReloadSubCommand;
import studios.littlesquad.welcomeflow.command.subcommand.ResetSubCommand;
import studios.littlesquad.welcomeflow.command.subcommand.SkipSubCommand;
import studios.littlesquad.welcomeflow.command.subcommand.StartSubCommand;
import studios.littlesquad.welcomeflow.command.subcommand.StatsSubCommand;
import studios.littlesquad.welcomeflow.command.subcommand.StatusSubCommand;
import studios.littlesquad.welcomeflow.command.subcommand.TestSubCommand;
import studios.littlesquad.welcomeflow.command.subcommand.ValidateSubCommand;
import studios.littlesquad.welcomeflow.config.ConfigIssue;
import studios.littlesquad.welcomeflow.config.ConfigManager;
import studios.littlesquad.welcomeflow.config.ConfigValidator;
import studios.littlesquad.welcomeflow.config.MessageService;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.flow.FlowRegistry;
import studios.littlesquad.welcomeflow.objective.listener.ObjectiveListeners;
import studios.littlesquad.welcomeflow.retention.RetentionSettings;
import studios.littlesquad.welcomeflow.retention.ReturnBonusService;
import studios.littlesquad.welcomeflow.retention.SecondJoinService;
import studios.littlesquad.welcomeflow.reward.RewardService;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.session.SessionLifecycleListener;
import studios.littlesquad.welcomeflow.session.SessionManager;
import studios.littlesquad.welcomeflow.storage.FunnelEvent;
import studios.littlesquad.welcomeflow.storage.SqliteStorageProvider;
import studios.littlesquad.welcomeflow.storage.StorageException;
import studios.littlesquad.welcomeflow.storage.StorageProvider;
import studios.littlesquad.welcomeflow.ui.ChecklistBossBarObserver;
import studios.littlesquad.welcomeflow.ui.ChecklistBossBarService;
import studios.littlesquad.welcomeflow.util.Scheduling;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Bootstrap: lifecycle and dependency wiring. Everything below is
 * constructor-injected from here; {@link WelcomeFlowAPI} is the only
 * static singleton in the project (see PLAN.md architecture notes).
 *
 * <p>M1 wires config/message loading and the command skeleton. The
 * storage layer, session manager, FlowEngine and objective/action
 * registries are added in M2 and wired in here at that point.
 */
public final class WelcomeFlowPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messageService;
    private ConfigValidator configValidator;
    private Scheduling scheduling;
    private StorageProvider storage;
    private SessionManager sessionManager;
    private ActionRegistry actionRegistry;
    private ActionRunner actionRunner;
    private FlowRegistry flowRegistry;
    private FlowEngine flowEngine;
    private ObjectiveListeners objectiveListeners;
    private ChecklistBossBarService bossBars;
    private RewardService rewardService;
    private SecondJoinService secondJoinService;
    private ReturnBonusService returnBonusService;
    private List<Action> secondJoinActions = List.of();
    private List<Action> returnBonusActions = List.of();

    @Override
    public void onEnable() {
        long startNanos = System.nanoTime();

        configManager = new ConfigManager(this);
        configManager.load();

        messageService = new MessageService(this);
        messageService.load();

        scheduling = new Scheduling(this);

        if (!initialiseStorage()) {
            // Without storage nothing can be persisted and a half-working
            // onboarding is worse than none: disable rather than limp on.
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        actionRegistry = new ActionRegistry();
        actionRunner = new ActionRunner(scheduling, getLogger());
        registerActionTypes();

        flowRegistry = new FlowRegistry(actionRegistry);
        sessionManager = new SessionManager(storage, scheduling, getLogger());
        flowEngine = new FlowEngine(
                flowRegistry, sessionManager, actionRunner, storage, scheduling, configManager, getLogger());

        configValidator = new ConfigValidator(configManager, flowRegistry, actionRegistry);

        loadFlows();

        objectiveListeners = new ObjectiveListeners(this, flowEngine, getLogger());
        objectiveListeners.registerFor(flowRegistry.usedObjectiveTypes());

        bossBars = new ChecklistBossBarService(configManager, messageService);
        flowEngine.addObserver(new ChecklistBossBarObserver(bossBars));

        initialiseRetention();

        getServer().getPluginManager().registerEvents(
                new SessionLifecycleListener(sessionManager, flowEngine, getLogger(), this::evaluateRetention),
                this);

        sessionManager.startPeriodicFlush(configManager.periodicFlushIntervalSeconds());
        sweepAbandonedFlows();

        registerCommand();
        logSoftDependencies();

        WelcomeFlowAPI.register(this);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        getLogger().info("WelcomeFlow enabled in " + elapsedMs + "ms.");
    }

    @Override
    public void onDisable() {
        WelcomeFlowAPI.unregister();

        if (objectiveListeners != null) {
            objectiveListeners.unregisterAll();
        }
        if (bossBars != null) {
            // Bossbars are client-side state we own: without this they stay on
            // screen after the plugin stops, until the player relogs.
            bossBars.hideAll(getServer().getOnlinePlayers());
        }
        if (sessionManager != null) {
            sessionManager.stopPeriodicFlush();
            // Synchronous on purpose: the scheduler is shutting down, so an
            // async flush here would simply never run and we would lose every
            // online player's progress.
            sessionManager.flushAllBlocking();
        }
        if (storage != null) {
            storage.close();
        }
        getLogger().info("WelcomeFlow disabled.");
    }

    private boolean initialiseStorage() {
        String type = configManager.storageType();
        if (!"sqlite".equalsIgnoreCase(type)) {
            getLogger().warning("storage.type is '" + type + "', but only 'sqlite' is supported in 1.0.0. "
                    + "Falling back to SQLite.");
        }
        File databaseFile = new File(getDataFolder(), configManager.storageFile());
        SqliteStorageProvider provider = new SqliteStorageProvider(databaseFile);
        try {
            // Timed and logged because this dominates enable time: the JDBC
            // driver extracts and loads a native library on its first
            // connection. Surfacing the number separately makes it obvious
            // that a "slow startup" report is the driver, not our code.
            long storageStartNanos = System.nanoTime();
            provider.initialize();
            long storageMs = (System.nanoTime() - storageStartNanos) / 1_000_000L;
            storage = provider;
            getLogger().info("Storage ready (" + provider.name() + ", " + databaseFile.getName()
                    + ") in " + storageMs + "ms.");
            return true;
        } catch (StorageException e) {
            getLogger().log(Level.SEVERE, "Could not open WelcomeFlow's database. "
                    + "Check the data folder is writable and that no other server is using it.", e);
            return false;
        }
    }

    private void registerActionTypes() {
        TextActions.registerInto(actionRegistry, configManager, scheduling);
        EffectActions.registerInto(actionRegistry);
        WorldActions.registerInto(actionRegistry, configManager);
        ControlActions.registerInto(
                actionRegistry,
                // Lazily supplied: the runner exists already, but referencing it
                // through a supplier keeps the registrar independent of wiring
                // order if that ever changes.
                () -> actionRunner,
                (entries, path) -> {
                    List<ConfigIssue> nested = new ArrayList<>();
                    List<Action> parsed = flowRegistry.parseActionList(entries, path, nested);
                    if (!nested.isEmpty()) {
                        throw new ActionParseException(nested.get(0).message());
                    }
                    return parsed;
                });
        getLogger().info("Registered " + actionRegistry.size() + " action types.");
    }

    /**
     * Builds the retention layer.
     *
     * <p>The reward action lists are parsed once here rather than per grant,
     * so a typo in {@code second-join.actions} surfaces at startup alongside
     * every other config problem instead of the first time somebody logs in
     * for the second time.
     */
    private void initialiseRetention() {
        List<ConfigIssue> issues = new ArrayList<>();
        secondJoinActions = parseRewardActions("second-join.actions", issues);
        returnBonusActions = parseRewardActions("return-bonus.actions", issues);
        reportIssues(issues);

        rewardService = new RewardService(actionRunner, sessionManager, getLogger(),
                rewardKey -> flowEngine.incrementFunnel(FunnelEvent.GLOBAL_SCOPE, rewardKey));

        // Settings are re-read per evaluation so /wf reload takes effect
        // without rebuilding the services or losing in-flight guards.
        Supplier<RetentionSettings> settings = () -> RetentionSettings.from(configManager);
        secondJoinService = new SecondJoinService(settings, rewardService, () -> secondJoinActions);
        returnBonusService = new ReturnBonusService(settings, rewardService, () -> returnBonusActions);
    }

    private List<Action> parseRewardActions(String path, List<ConfigIssue> issues) {
        List<?> raw = configManager.raw().getList(path);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return flowRegistry.parseActionList(raw, path, issues);
    }

    /**
     * Runs the retention checks for a player whose session just loaded.
     *
     * <p>Evaluated before flow assignment so a returning player's reward lands
     * ahead of any tutorial text, and guarded so a reward failure can never
     * stop them being onboarded.
     */
    private void evaluateRetention(org.bukkit.entity.Player player, PlayerSession session) {
        try {
            secondJoinService.evaluate(player, session);
            returnBonusService.evaluate(player, session);
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING,
                    "Retention reward check failed for " + player.getName() + "; continuing without it.", e);
        }
    }

    /** Loads flows and reports every config problem in one pass. */
    private void loadFlows() {
        List<ConfigIssue> issues = flowRegistry.load(configManager.rawFlows().getConfigurationSection("flows"));
        reportIssues(issues);
        getLogger().info("Loaded " + flowRegistry.all().size() + " flow(s): "
                + String.join(", ", flowRegistry.flowIds()));
    }

    private void reportIssues(List<ConfigIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }
        long errors = issues.stream().filter(ConfigIssue::isError).count();
        long warnings = issues.size() - errors;
        getLogger().warning("Configuration check found " + errors + " error(s) and " + warnings + " warning(s):");
        for (ConfigIssue issue : issues) {
            String line = "  [" + issue.severity() + "] " + issue.describe();
            if (issue.isError()) {
                getLogger().severe(line);
            } else {
                getLogger().warning(line);
            }
        }
        getLogger().warning("Run /wf validate after fixing to re-check without a restart.");
    }

    /**
     * Reclassifies interrupted runs that are past their flow's abandon window.
     *
     * <p>Runs once at startup rather than on a timer: an abandoned tutorial is
     * not time-sensitive, and this keeps the plugin free of periodic scanning.
     * Uses the shortest configured window so no flow's setting is exceeded.
     */
    private void sweepAbandonedFlows() {
        long shortestWindow = flowRegistry.all().stream()
                .map(flow -> flow.settings().abandonAfterMillis())
                .filter(millis -> millis > 0)
                .min(Long::compareTo)
                .orElse(0L);
        if (shortestWindow <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - shortestWindow;
        scheduling.runAsync(() -> {
            try {
                int changed = storage.markStaleProgressAbandoned(cutoff);
                if (changed > 0) {
                    getLogger().info("Marked " + changed + " interrupted flow run(s) as abandoned.");
                }
            } catch (StorageException e) {
                getLogger().log(Level.WARNING, "Could not sweep abandoned flow progress.", e);
            }
        });
    }

    private void registerCommand() {
        List<SubCommand> subCommands = List.of(
                new StartSubCommand(messageService),
                new ResetSubCommand(messageService),
                new SkipSubCommand(messageService),
                new StatusSubCommand(messageService),
                new StatsSubCommand(messageService),
                new ValidateSubCommand(messageService),
                new TestSubCommand(messageService),
                new ReloadSubCommand(configManager, messageService, getLogger()));

        WelcomeFlowCommand command = new WelcomeFlowCommand(messageService, subCommands);

        // Paper plugins do not support YAML command declarations, so there is
        // no PluginCommand to fetch — commands go through the Brigadier
        // registrar. This overload exists identically in 1.21.4 and 26.1.2.
        registerCommand(
                "welcomeflow",
                "WelcomeFlow onboarding and retention system.",
                List.of("wflow", "wf"),
                command);
    }

    private void logSoftDependencies() {
        boolean placeholderApi = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        boolean vault = getServer().getPluginManager().isPluginEnabled("Vault");
        getLogger().info("PlaceholderAPI: " + (placeholderApi ? "detected" : "not found (optional)"));
        getLogger().info("Vault: " + (vault ? "detected" : "not found (optional)"));
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public ConfigValidator getConfigValidator() {
        return configValidator;
    }
}
