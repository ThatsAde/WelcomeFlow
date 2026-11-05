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
import studios.littlesquad.welcomeflow.api.event.EventPublishingObserver;
import studios.littlesquad.welcomeflow.api.event.RetentionRewardEvent;
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
import studios.littlesquad.welcomeflow.flow.Flow;
import studios.littlesquad.welcomeflow.flow.FlowEngine;
import studios.littlesquad.welcomeflow.flow.FlowRegistry;
import studios.littlesquad.welcomeflow.integration.PlaceholderApiHook;
import studios.littlesquad.welcomeflow.integration.WelcomeFlowExpansion;
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
        // The public API is just another observer -- adding it required no
        // change to the engine's transition logic.
        flowEngine.addObserver(new EventPublishingObserver());
        // Discards /wf test data the moment the test flow finishes.
        flowEngine.addObserver(new studios.littlesquad.welcomeflow.flow.FlowObserver() {
            @Override
            public void onFlowActionsComplete(org.bukkit.entity.Player player, PlayerSession session, Flow flow) {
                if (session.temporary()) scheduling.runSyncLater(() -> {
                    if (sessionManager.get(player.getUniqueId()) == session)
                        endTestRunIfActive(player.getUniqueId(), player.getName());
                }, 1);
            }
        });

        initialiseRetention();
        reportIssues(validateFiles());

        getServer().getPluginManager().registerEvents(
                new SessionLifecycleListener(sessionManager, flowEngine, getLogger(),
                        this::evaluateRetention, this::endTestRunIfActive),
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
        configureActions(actionRegistry, configManager);
        getLogger().info("Registered " + actionRegistry.size() + " action types.");
    }

    private void configureActions(ActionRegistry registry, ConfigManager settings) {
        TextActions.registerInto(registry, settings, scheduling);
        EffectActions.registerInto(registry);
        WorldActions.registerInto(registry, settings);
        FlowRegistry parser = new FlowRegistry(registry);
        ControlActions.registerInto(
                registry,
                // Lazily supplied: the runner exists already, but referencing it
                // through a supplier keeps the registrar independent of wiring
                // order if that ever changes.
                () -> actionRunner,
                (entries, path) -> {
                    List<ConfigIssue> nested = new ArrayList<>();
                    List<Action> parsed = parser.parseActionList(entries, path, nested);
                    if (!nested.isEmpty()) {
                        throw new ActionParseException(nested.get(0).message());
                    }
                    return parsed;
                });
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

        rewardService = new RewardService(
                actionRunner, sessionManager, getLogger(),
                rewardKey -> flowEngine.incrementFunnel(FunnelEvent.GLOBAL_SCOPE, rewardKey),
                this::isRewardVetoed);

        // Settings are re-read per evaluation so /wf reload takes effect
        // without rebuilding the services or losing in-flight guards.
        Supplier<RetentionSettings> settings = () -> RetentionSettings.from(configManager);
        secondJoinService = new SecondJoinService(settings, rewardService, () -> secondJoinActions);
        returnBonusService = new ReturnBonusService(settings, rewardService, () -> returnBonusActions);
    }

    /**
     * Fires the cancellable {@link RetentionRewardEvent} and reports whether a
     * listener withheld the reward.
     */
    private boolean isRewardVetoed(org.bukkit.entity.Player player, String rewardKey) {
        PlayerSession session = sessionManager.get(player.getUniqueId());
        RetentionRewardEvent.Type type = SecondJoinService.REWARD_KEY.equals(rewardKey)
                ? RetentionRewardEvent.Type.SECOND_JOIN
                : RetentionRewardEvent.Type.RETURN_BONUS;
        int claimNumber = session == null || type == RetentionRewardEvent.Type.SECOND_JOIN
                ? 1
                : session.record().returnBonusClaims();

        RetentionRewardEvent event = new RetentionRewardEvent(player, type, claimNumber);
        getServer().getPluginManager().callEvent(event);
        return event.isCancelled();
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
        List<Flow> definitions = List.copyOf(flowRegistry.all());
        scheduling.runAsync(() -> {
            try {
                int changed = 0;
                for (Flow flow : definitions) {
                    long window = flow.settings().abandonAfterMillis();
                    if (window > 0) changed += storage.markStaleProgressAbandoned(
                            flow.id(), System.currentTimeMillis() - window);
                }
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
                new StartSubCommand(this, messageService),
                new ResetSubCommand(this, messageService),
                new SkipSubCommand(this, messageService),
                new StatusSubCommand(this, messageService),
                new StatsSubCommand(this, messageService),
                new ValidateSubCommand(this, messageService),
                new TestSubCommand(this, messageService),
                new ReloadSubCommand(this, messageService, getLogger()));

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

        if (placeholderApi) {
            registerPlaceholderExpansion();
        }
    }

    /**
     * Registers our placeholder expansion.
     *
     * <p>Isolated in its own method and guarded against {@link LinkageError}
     * as well as exceptions: if PlaceholderAPI is present but a different
     * major version than we compiled against, the class-loading failure must
     * cost us the placeholders, not the whole plugin.
     */
    private void registerPlaceholderExpansion() {
        try {
            new WelcomeFlowExpansion(this, returnBonusService).register();
            getLogger().info("Registered %welcomeflow_*% placeholders.");
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.WARNING,
                    "PlaceholderAPI is installed but the expansion could not be registered; "
                            + "%welcomeflow_*% placeholders will not resolve.", e);
        }
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

    // --- accessors used by WelcomeFlowAPI and the command tree ------------

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public FlowRegistry flowRegistry() {
        return flowRegistry;
    }

    public FlowEngine flowEngine() {
        return flowEngine;
    }

    public StorageProvider storage() {
        return storage;
    }

    public Scheduling scheduling() {
        return scheduling;
    }

    public SecondJoinService secondJoinService() {
        return secondJoinService;
    }

    public ReturnBonusService returnBonusService() {
        return returnBonusService;
    }

    /**
     * Starts a named flow for an online player, bypassing its conditions.
     *
     * @return false if the flow is unknown, the player has no session, or
     *     they are already in a flow
     */
    public boolean startFlowFor(org.bukkit.entity.Player player, String flowId) {
        Flow flow = flowRegistry.byId(flowId);
        if (flow == null) {
            return false;
        }
        PlayerSession session = sessionManager.get(player.getUniqueId());
        if (session == null || session.isInFlow()) {
            return false;
        }
        flowEngine.startFlow(player, session, flow);
        return session.progress() != null && session.progress().flowId().equals(flow.id());
    }

    /**
     * Players currently running {@code /wf test}, whose data is discarded when
     * the run ends. Small and admin-only, so a plain set is fine.
     */

    /**
     * Puts a player into a throwaway test run.
     *
     * <p>Clears their live progress in place so the flow starts from step 0,
     * and records them so {@link #endTestRunIfActive} wipes the persisted
     * data once they finish or log out. The flow itself runs completely
     * normally -- the admin sees exactly what a real new player would.
     */
    public PlayerSession beginTestRun(org.bukkit.entity.Player player, PlayerSession session) {
        bossBars.hide(player);
        return sessionManager.beginPreview(player.getUniqueId());
    }

    /** True while the player is inside a {@code /wf test} run. */
    public boolean isTestRun(java.util.UUID uuid) {
        return sessionManager.isPreview(uuid);
    }

    /**
     * Ends a test run and discards everything it wrote.
     *
     * <p>Called on flow completion and on quit, so a test never leaves the
     * admin's real progress or reward flags altered.
     */
    public void endTestRunIfActive(java.util.UUID uuid, String playerName) {
        PlayerSession original = sessionManager.endPreview(uuid);
        org.bukkit.entity.Player player = getServer().getPlayer(uuid);
        if (original != null && player != null && player.isOnline()) {
            bossBars.hide(player);
            flowEngine.refresh(player, original, null);
        }
    }

    /**
     * Wipes a player's stored progress and reward flags.
     *
     * <p>The in-memory session is dropped first so nothing can re-save the
     * data we are about to delete, then the delete runs off-thread.
     */
    public void resetPlayerData(java.util.UUID uuid, String playerName) {
        org.bukkit.entity.Player online = getServer().getPlayer(uuid);
        if (online != null) {
            bossBars.hide(online);
        }
        rewardService.forget(uuid);
        sessionManager.reset(uuid, () -> {
            org.bukkit.entity.Player current = getServer().getPlayer(uuid);
            if (current != null && current.isOnline() && !sessionManager.isLoaded(uuid)) {
                sessionManager.loadOnJoin(current, session -> { });
            }
        });
    }

    /**
     * Re-reads every config file and rebuilds what depends on it.
     *
     * <p>Active sessions are preserved: only definitions are replaced, so a
     * player mid-flow keeps their progress. Objective listeners are
     * re-registered because the new flows.yml may use a different set of
     * objective types than the old one.
     *
     * @return the issues found; empty means a clean reload
     */
    public List<ConfigIssue> reloadEverything() {
        Candidate candidate = readCandidate();
        if (candidate.issues().stream().anyMatch(ConfigIssue::isError)) return candidate.issues();
        java.util.Map<String, Flow> previous = new java.util.HashMap<>();
        flowRegistry.all().forEach(flow -> previous.put(flow.id(), flow));
        configManager.apply(candidate.config());
        messageService.apply(candidate.messages());
        flowRegistry.apply(candidate.flows());
        secondJoinActions = candidate.secondJoin();
        returnBonusActions = candidate.returnBonus();
        PlaceholderApiHook.invalidate();
        objectiveListeners.registerFor(flowRegistry.usedObjectiveTypes());
        bossBars.hideAll(getServer().getOnlinePlayers());
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            PlayerSession session = sessionManager.get(player.getUniqueId());
            if (session != null) {
                // Restore parked real progress before reconciling against new definitions.
                PlayerSession original = sessionManager.endPreview(player.getUniqueId());
                if (original != null) session = original;
                Flow old = session.progress() == null ? null : previous.get(session.progress().flowId());
                flowEngine.refresh(player, session, old);
            }
        }
        sessionManager.startPeriodicFlush(configManager.periodicFlushIntervalSeconds());
        return candidate.issues();
    }

    public List<ConfigIssue> validateFiles() { return readCandidate().issues(); }

    private record Candidate(ConfigManager config, org.bukkit.configuration.file.YamlConfiguration messages,
                             FlowRegistry flows, List<Action> secondJoin, List<Action> returnBonus,
                             List<ConfigIssue> issues) { }

    private Candidate readCandidate() {
        List<ConfigIssue> issues = new ArrayList<>();
        for (String name : List.of("config.yml", "flows.yml", "messages.yml")) {
            try {
                new org.bukkit.configuration.file.YamlConfiguration().load(new File(getDataFolder(), name));
            } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                issues.add(ConfigIssue.error(name, "root", "Could not parse YAML: " + e.getMessage()));
            }
        }
        if (!issues.isEmpty()) return new Candidate(null, null, null, List.of(), List.of(), List.copyOf(issues));
        try {
            ConfigManager settings = configManager.readCandidate();
            var messages = configManager.readMessages();
            ActionRegistry actions = new ActionRegistry();
            configureActions(actions, settings);
            FlowRegistry flows = new FlowRegistry(actions);
            flows.load(settings.rawFlows().getConfigurationSection("flows"));
            ConfigValidator validator = new ConfigValidator(settings, flows, actions);
            issues.addAll(validator.validateAll());
            issues.addAll(validator.validateDocuments(messages));
            List<Action> second = flows.parseActionList(settings.raw().getList("second-join.actions", List.of()),
                    "second-join.actions", issues);
            List<Action> returning = flows.parseActionList(settings.raw().getList("return-bonus.actions", List.of()),
                    "return-bonus.actions", issues);
            // The flow parser reports its default filename; retention actions belong to config.yml.
            issues.replaceAll(issue -> issue.path().startsWith("second-join.") || issue.path().startsWith("return-bonus.")
                    ? new ConfigIssue(issue.severity(), "config.yml", issue.path(), issue.message()) : issue);
            if (!settings.storageFile().equals(configManager.storageFile())
                    || !settings.storageType().equals(configManager.storageType())) {
                issues.add(ConfigIssue.error("config.yml", "storage", "Changing the database requires a server restart."));
            }
            return new Candidate(settings, messages, flows, second, returning, List.copyOf(issues));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException e) {
            issues.add(ConfigIssue.error("configuration", "files", "Could not read configuration: " + e.getMessage()));
            return new Candidate(null, null, null, List.of(), List.of(), List.copyOf(issues));
        }
    }
}
