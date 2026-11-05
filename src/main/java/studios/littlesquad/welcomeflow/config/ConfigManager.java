package studios.littlesquad.welcomeflow.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    /** Strictly reads a candidate without changing live settings. */
    public ConfigManager readCandidate() throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        ConfigManager candidate = new ConfigManager(plugin);
        candidate.config = readStrict(configFile, "config.yml");
        candidate.flows = readStrict(flowsFile, "flows.yml");
        return candidate;
    }

    public YamlConfiguration readMessages() throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        return readStrict(new File(plugin.getDataFolder(), "messages.yml"), "messages.yml");
    }

    private YamlConfiguration readStrict(File file, String resource) throws IOException,
            org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); }
        catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new org.bukkit.configuration.InvalidConfigurationException(resource + ": " + e.getMessage());
        }
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        return yaml;
    }

    public void apply(ConfigManager candidate) {
        config = candidate.config;
        flows = candidate.flows;
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

    // --- named locations --------------------------------------------------

    /**
     * Resolves a named location from the {@code locations} block, so flows
     * can say {@code 'teleport: spawn'} instead of repeating coordinates.
     *
     * <p>The special name {@code spawn} falls back to the main world's spawn
     * point when it is not explicitly configured; that is what makes the
     * bundled flow work with zero edits.
     *
     * @return the location, or {@code null} if the name is unknown or its
     *     world is not loaded
     */
    public Location namedLocation(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = "locations." + name.trim();
        ConfigurationSection section = config.getConfigurationSection(key);
        if (section == null) {
            return "spawn".equalsIgnoreCase(name.trim()) ? defaultSpawn() : null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return "spawn".equalsIgnoreCase(name.trim()) ? defaultSpawn() : null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0));
    }

    /** Names declared under {@code locations}, for validation and tab completion. */
    public Set<String> namedLocationKeys() {
        ConfigurationSection section = config.getConfigurationSection("locations");
        return section == null ? Set.of() : Set.copyOf(section.getKeys(false));
    }

    private Location defaultSpawn() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0).getSpawnLocation();
    }

    // --- UI defaults ------------------------------------------------------

    public int titleDefaultFadeInTicks() {
        return config.getInt("ui.title.default-fade-in-ticks", 10);
    }

    public int titleDefaultStayTicks() {
        return config.getInt("ui.title.default-stay-ticks", 60);
    }

    public int titleDefaultFadeOutTicks() {
        return config.getInt("ui.title.default-fade-out-ticks", 20);
    }

    public boolean bossBarEnabled() {
        return config.getBoolean("ui.bossbar.enabled", true);
    }

    public BossBar.Color bossBarColor() {
        return parseEnum(BossBar.Color.class, config.getString("ui.bossbar.color"), BossBar.Color.BLUE);
    }

    /**
     * The bossbar overlay, accepting either Adventure's own names
     * ({@code NOTCHED_10}, {@code PROGRESS}) or the Bukkit {@code BarStyle}
     * names most server owners already know ({@code SEGMENTED_10},
     * {@code SOLID}).
     *
     * <p>Supporting both costs one lookup table and removes a whole category
     * of "my config looks right but the bar is solid" support ticket.
     */
    public BossBar.Overlay bossBarStyle() {
        return parseEnum(
                BossBar.Overlay.class,
                normaliseOverlayName(config.getString("ui.bossbar.style")),
                BossBar.Overlay.NOTCHED_10);
    }

    /** Maps Bukkit BarStyle spellings onto Adventure's Overlay constants. */
    private static String normaliseOverlayName(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SOLID" -> "PROGRESS";
            case "SEGMENTED_6" -> "NOTCHED_6";
            case "SEGMENTED_10" -> "NOTCHED_10";
            case "SEGMENTED_12" -> "NOTCHED_12";
            case "SEGMENTED_20" -> "NOTCHED_20";
            default -> raw.trim().toUpperCase(Locale.ROOT);
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
