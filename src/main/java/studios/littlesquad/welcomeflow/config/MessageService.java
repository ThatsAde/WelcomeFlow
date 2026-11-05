package studios.littlesquad.welcomeflow.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import studios.littlesquad.welcomeflow.util.Text;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Loads messages.yml and turns message keys into rendered Components.
 * Every user-facing string in the plugin is requested from here rather
 * than written inline in Java -- see the "no hardcoded text anywhere in
 * Java" constraint in PLAN.md.
 */
public final class MessageService {

    private final JavaPlugin plugin;
    private final File messagesFile;
    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        try (InputStream defaultStream = plugin.getResource("messages.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load bundled defaults for messages.yml", e);
        }
    }

    public void reload() {
        load();
    }

    public void apply(YamlConfiguration candidate) { messages = candidate; }

    /** The plugin's chat prefix, already parsed. */
    public Component prefix() {
        return Text.parse(raw("prefix"));
    }

    public String raw(String path) {
        String value = messages.getString(path);
        if (value == null) {
            plugin.getLogger().warning("Missing message key: " + path);
            return "<red>Missing message: " + path;
        }
        return value;
    }

    public Component render(String path, TagResolver... resolvers) {
        return Text.parse(raw(path), resolvers);
    }

    /** {@link #render} with the plugin's prefix prepended. */
    public Component renderPrefixed(String path, TagResolver... resolvers) {
        return prefix().append(render(path, resolvers));
    }
}
