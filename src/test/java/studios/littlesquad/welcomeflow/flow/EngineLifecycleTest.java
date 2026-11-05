package studios.littlesquad.welcomeflow.flow;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.plugin.java.JavaPlugin;
import studios.littlesquad.welcomeflow.action.*;
import studios.littlesquad.welcomeflow.config.*;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;
import studios.littlesquad.welcomeflow.session.*;
import studios.littlesquad.welcomeflow.storage.*;
import studios.littlesquad.welcomeflow.util.Scheduling;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class EngineLifecycleTest {
    @TempDir Path directory;
    ServerMock server;
    PlayerMock player;
    JavaPlugin plugin;
    SqliteStorageProvider storage;
    SessionManager sessions;
    ConfigManager config;
    FlowRegistry registry;
    FlowEngine engine;
    ActionRunner runner;
    ActionRegistry actions;
    Scheduling scheduling;
    AtomicInteger messages;

    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer();
        for (String file : List.of("config.yml", "flows.yml", "messages.yml")) {
            Path destination = plugin.getDataFolder().toPath().resolve(file);
            Files.createDirectories(destination.getParent());
            try (var stream = getClass().getClassLoader().getResourceAsStream(file)) {
                Files.copy(Objects.requireNonNull(stream), destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        config = new ConfigManager(plugin);
        config.load();
        storage = new SqliteStorageProvider(directory.resolve("test.db").toFile());
        storage.initialize();
        scheduling = new Scheduling(plugin);
        sessions = new SessionManager(storage, scheduling, Logger.getAnonymousLogger());
        runner = new ActionRunner(scheduling, Logger.getAnonymousLogger());
        actions = new ActionRegistry();
        messages = new AtomicInteger();
        actions.register(new LambdaActionFactory("message", "message: text", spec -> ctx -> messages.incrementAndGet()));
        registry = new FlowRegistry(actions);
        engine = new FlowEngine(registry, sessions, runner, storage, scheduling, config, Logger.getAnonymousLogger());
        join();
    }

    void join() {
        sessions.loadOnJoin(player, session -> { });
        sessions.awaitStorage();
        server.getScheduler().performTicks(1);
        assertNotNull(sessions.get(player.getUniqueId()));
    }

    @AfterEach void cleanup() {
        if (sessions != null) sessions.flushAllBlocking();
        if (storage != null) storage.close();
        MockBukkit.unmock();
    }

    Flow load(String steps) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("flows:\n  welcome:\n    steps:\n" + steps);
        assertFalse(registry.load(yaml.getConfigurationSection("flows")).stream().anyMatch(ConfigIssue::isError));
        return registry.byId("welcome");
    }

    String step(String id, String type, int amount) {
        return "      - id: " + id + "\n        display: '<green>" + id + "'\n        objective:\n          type: "
                + type + "\n          amount: " + amount + "\n";
    }

    @Test void completesAndDoesNotResumeCompletedFlow() throws Exception {
        Flow flow = load(step("first", "MANUAL", 1) + step("second", "MANUAL", 2));
        PlayerSession session = sessions.get(player.getUniqueId());
        engine.startFlow(player, session, flow);
        engine.creditProgress(player, ObjectiveType.MANUAL, 1);
        assertEquals(1, session.progress().stepIndex());
        engine.creditProgress(player, ObjectiveType.MANUAL, 2);
        sessions.flush(session);
        sessions.awaitStorage();
        assertNull(storage.loadActiveProgress(player.getUniqueId()));
        assertEquals(List.of("welcome"), storage.completedFlowIds(player.getUniqueId()));
        assertNull(session.progress());
    }

    @Test void relogRestoresPartialProgress() throws Exception {
        Flow flow = load(step("walk", "WALK_DISTANCE", 25));
        engine.startFlow(player, sessions.get(player.getUniqueId()), flow);
        engine.creditProgress(player, ObjectiveType.WALK_DISTANCE, 7);
        sessions.unloadOnQuit(player.getUniqueId());
        join();
        PlayerSession resumed = sessions.get(player.getUniqueId());
        engine.onJoin(player, resumed);
        assertEquals(7, resumed.progress().stepProgress());
    }

    @Test void previewPreservesOriginalFlagsAndProgress() throws Exception {
        Flow flow = load(step("walk", "WALK_DISTANCE", 25));
        PlayerSession original = sessions.get(player.getUniqueId());
        original.record().secondJoinClaimed(true);
        engine.startFlow(player, original, flow);
        engine.creditProgress(player, ObjectiveType.WALK_DISTANCE, 7);
        PlayerSession preview = sessions.beginPreview(player.getUniqueId());
        engine.startFlow(player, preview, flow);
        engine.forceCompleteCurrentStep(player);
        assertSame(original, sessions.endPreview(player.getUniqueId()));
        sessions.awaitStorage();
        assertEquals(7, storage.loadActiveProgress(player.getUniqueId()).stepProgress());
        assertTrue(storage.loadPlayer(player.getUniqueId()).secondJoinClaimed());
        assertTrue(storage.completedFlowIds(player.getUniqueId()).isEmpty());
        assertTrue(original.isInFlow());
    }

    @Test void previewQuitRestoresAndSavesRealSession() throws Exception {
        Flow flow = load(step("walk", "WALK_DISTANCE", 25));
        engine.startFlow(player, sessions.get(player.getUniqueId()), flow);
        engine.creditProgress(player, ObjectiveType.WALK_DISTANCE, 9);
        sessions.beginPreview(player.getUniqueId());
        sessions.unloadOnQuit(player.getUniqueId());
        sessions.awaitStorage();
        assertEquals(9, storage.loadActiveProgress(player.getUniqueId()).stepProgress());
    }

    @Test void reloadMapsStepByIdInsteadOfIndex() throws Exception {
        Flow old = load(step("first", "MANUAL", 1) + step("second", "MANUAL", 5));
        PlayerSession session = sessions.get(player.getUniqueId());
        engine.startFlow(player, session, old);
        engine.forceCompleteCurrentStep(player);
        engine.creditProgress(player, ObjectiveType.MANUAL, 2);
        load(step("second", "MANUAL", 5) + step("first", "MANUAL", 1));
        engine.refresh(player, session, old);
        assertEquals(0, session.progress().stepIndex());
        assertEquals(2, session.progress().stepProgress());
    }

    @Test void playTimeResumesWithOnlyRemainingTime() throws Exception {
        Flow flow = load(step("wait", "PLAY_TIME", 20));
        PlayerSession session = sessions.get(player.getUniqueId());
        engine.startFlow(player, session, flow);
        session.startPlayTime(System.currentTimeMillis() - 12_000);
        sessions.unloadOnQuit(player.getUniqueId());
        join();
        session = sessions.get(player.getUniqueId());
        assertTrue(session.progress().stepProgress() >= 12);
        engine.onJoin(player, session);
        server.getScheduler().performTicks(160);
        assertNull(session.progress());
    }

    @Test void oldPlayTimeTimerCannotCompleteRestartedStep() throws Exception {
        Flow flow = load(step("wait", "PLAY_TIME", 20));
        PlayerSession session = sessions.get(player.getUniqueId());
        engine.startFlow(player, session, flow);
        server.getScheduler().performTicks(200);
        engine.startFlow(player, session, flow);
        server.getScheduler().performTicks(200);
        assertNotNull(session.progress());
        server.getScheduler().performTicks(200);
        assertNull(session.progress());
    }

    @Test void delayedActionsStopWhenSessionInvalidated() {
        PlayerSession session = sessions.get(player.getUniqueId());
        runner.run(List.of(new DelayAction(10), ctx -> messages.incrementAndGet()),
                ActionContext.ofFlow(player, session, "welcome"));
        session.invalidateTasks();
        server.getScheduler().performTicks(10);
        assertEquals(0, messages.get());
    }

    @Test void cancelledStartHasNoProgress() throws Exception {
        engine.addObserver(new FlowObserver() {
            @Override public boolean allowFlowStart(org.bukkit.entity.Player p, PlayerSession s, Flow f, boolean resumed) {
                return false;
            }
        });
        engine.startFlow(player, sessions.get(player.getUniqueId()), load(step("manual", "MANUAL", 1)));
        assertNull(sessions.get(player.getUniqueId()).progress());
    }

    @Test void abandonActionsActuallyRun() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("flows:\n  welcome:\n    on-flow-abandon: ['message: goodbye']\n    steps:\n" + step("manual", "MANUAL", 1));
        registry.load(yaml.getConfigurationSection("flows"));
        PlayerSession session = sessions.get(player.getUniqueId());
        engine.startFlow(player, session, registry.byId("welcome"));
        engine.onQuit(player, session);
        assertEquals(1, messages.get());
    }

    @Test void resetRunsAfterPreviouslyQueuedWrites() throws Exception {
        PlayerSession session = sessions.get(player.getUniqueId());
        engine.startFlow(player, session, load(step("manual", "MANUAL", 500)));
        for (int i = 0; i < 100; i++) {
            engine.creditProgress(player, ObjectiveType.MANUAL, 1);
            sessions.flush(session);
        }
        sessions.reset(player.getUniqueId(), () -> { });
        sessions.awaitStorage();
        assertNull(storage.loadActiveProgress(player.getUniqueId()));
        assertEquals(0, storage.loadPlayer(player.getUniqueId()).joinCount());
    }

    @Test void validatesFilesWithoutApplyingThem() throws Exception {
        boolean debug = config.isDebug();
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "debug: " + !debug + "\n");
        ConfigManager candidate = config.readCandidate();
        assertEquals(!debug, candidate.isDebug());
        assertEquals(debug, config.isDebug());
    }

    @Test void cyclicPrerequisitesAreRejected() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("flows:\n  a:\n    conditions:\n      previous-flow-completed: b\n    steps:\n"
                + step("one", "MANUAL", 1) + "  b:\n    conditions:\n      previous-flow-completed: a\n    steps:\n" + step("two", "MANUAL", 1));
        registry.load(yaml.getConfigurationSection("flows"));
        assertTrue(new ConfigValidator(config, registry, actions).validateAll().stream()
                .anyMatch(issue -> issue.message().contains("Circular")));
    }

    @Test void shutdownBanksFirstSessionTime() {
        PlayerSession session = sessions.get(player.getUniqueId());
        sessions.flushAllBlocking();
        sessions = null;
        assertEquals(session.record().firstSessionSeconds(), storage.loadPlayer(player.getUniqueId()).firstSessionSeconds());
        assertTrue(storage.loadPlayer(player.getUniqueId()).lastSeenAt() >= session.sessionStartedAt());
    }

    @Test void rewardIsGrantedOnceAcrossRepeatedEvaluationAndReload() {
        var rewards = new studios.littlesquad.welcomeflow.reward.RewardService(runner, sessions,
                Logger.getAnonymousLogger(), key -> { }, (p, key) -> false);
        var settings = new studios.littlesquad.welcomeflow.retention.RetentionSettings(true, 2, 0, false, 12, 20, 0);
        var service = new studios.littlesquad.welcomeflow.retention.SecondJoinService(() -> settings, rewards,
                () -> List.of(ctx -> messages.incrementAndGet()));
        PlayerSession session = sessions.get(player.getUniqueId());
        session.record().incrementJoinCount();
        assertTrue(service.evaluate(player, session));
        assertFalse(service.evaluate(player, session));
        sessions.unloadOnQuit(player.getUniqueId());
        join();
        assertFalse(service.evaluate(player, sessions.get(player.getUniqueId())));
        assertEquals(1, messages.get());
    }

    @Test void invalidYamlDoesNotReplaceLiveConfiguration() throws Exception {
        Files.writeString(plugin.getDataFolder().toPath().resolve("flows.yml"), "flows: [unterminated");
        assertThrows(org.bukkit.configuration.InvalidConfigurationException.class, config::readCandidate);
        assertNotNull(config.rawFlows().getConfigurationSection("flows"));
    }

    @Test void documentValidationCoversMessagesGlobalsAndMissingLocations() throws Exception {
        ConfigManager candidate = config.readCandidate();
        candidate.raw().set("ui.bossbar.color", "NO_SUCH_COLOR");
        candidate.raw().set("return-bonus.cooldown-hours", -1);
        candidate.raw().set("debug", "yes please");
        YamlConfiguration messagesYaml = config.readMessages();
        messagesYaml.set("prefix", "<greeen>Typo");
        candidate.rawFlows().set("flows.welcome.on-flow-start", List.of("teleport: unknown-location"));
        var issues = new ConfigValidator(candidate, registry, actions).validateDocuments(messagesYaml);
        assertTrue(issues.stream().anyMatch(i -> i.path().equals("ui.bossbar.color") && i.isError()));
        assertTrue(issues.stream().anyMatch(i -> i.file().equals("messages.yml") && i.message().contains("greeen")));
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("unknown-location")));
    }

    @Test void bundledDocumentsPassFullValidation() throws Exception {
        var issues = new ConfigValidator(config, registry, actions).validateDocuments(config.readMessages());
        assertTrue(issues.isEmpty(), issues.toString());
    }
}
