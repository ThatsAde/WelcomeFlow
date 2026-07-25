package studios.littlesquad.welcomeflow;

import org.bukkit.plugin.java.JavaPlugin;
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
import studios.littlesquad.welcomeflow.config.ConfigManager;
import studios.littlesquad.welcomeflow.config.ConfigValidator;
import studios.littlesquad.welcomeflow.config.MessageService;

import java.util.List;

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

    @Override
    public void onEnable() {
        long startNanos = System.nanoTime();

        configManager = new ConfigManager(this);
        configManager.load();

        messageService = new MessageService(this);
        messageService.load();

        configValidator = new ConfigValidator(configManager);

        registerCommand();
        logSoftDependencies();

        WelcomeFlowAPI.register(this);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        getLogger().info("WelcomeFlow enabled in " + elapsedMs + "ms.");
    }

    @Override
    public void onDisable() {
        WelcomeFlowAPI.unregister();
        getLogger().info("WelcomeFlow disabled.");
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
