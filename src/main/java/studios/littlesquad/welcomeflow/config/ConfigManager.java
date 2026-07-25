package studios.littlesquad.welcomeflow.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Loads and exposes config.yml and the raw flows.yml document. Flow
 * parsing itself (Flow/Step/FlowRegistry) is built in M2; this class
 * only owns file I/O, defaults merging and typed accessors for the
 * global settings in config.yml.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private final File flowsFile;
    private YamlConfiguration config;
    private YamlConfiguration flows;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.flowsFile = new File(plugin.getDataFolder(), "flows.yml");
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        saveDefaultIfMissing("config.yml", configFile);
        saveDefaultIfMissing("flows.yml", flowsFile);
        config = loadWithDefaults(configFile, "config.yml");
        flows = loadWithDefaults(flowsFile, "flows.yml");
    }

    public void reload() {
        load();
    }

    private void saveDefaultIfMissing(String resourceName, File target) {
        if (!target.exists()) {
            plugin.saveResource(resourceName, false);
        }
    }

    private YamlConfiguration loadWithDefaults(File file, String resourceName) {
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaultStream = plugin.getResource(resourceName)) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                loaded.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load bundled defaults for " + resourceName, e);
        }
        return loaded;
    }

    public YamlConfiguration raw() {
        return config;
    }

    public YamlConfiguration rawFlows() {
        return flows;
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    public String storageType() {
        return config.getString("storage.type", "sqlite");
    }

    public String storageFile() {
        return config.getString("storage.file", "data.db");
    }

    public int periodicFlushIntervalSeconds() {
        return config.getInt("storage.periodic-flush-interval-seconds", 120);
    }

    public boolean secondJoinEnabled() {
        return config.getBoolean("second-join.enabled", true);
    }

    public int secondJoinTriggerJoinNumber() {
        return config.getInt("second-join.trigger-join-number", 2);
    }

    public int secondJoinMinimumFirstSessionSeconds() {
        return config.getInt("second-join.minimum-first-session-seconds", 120);
    }

    public boolean returnBonusEnabled() {
        return config.getBoolean("return-bonus.enabled", true);
    }

    public int returnBonusMinimumHoursAway() {
        return config.getInt("return-bonus.minimum-hours-away", 12);
    }

    public int returnBonusCooldownHours() {
        return config.getInt("return-bonus.cooldown-hours", 20);
    }

    public int returnBonusMaxClaims() {
        return config.getInt("return-bonus.max-claims", 0);
    }

    public boolean placeholderApiResolveInMessages() {
        return config.getBoolean("placeholderapi.resolve-in-messages", true);
    }
}
