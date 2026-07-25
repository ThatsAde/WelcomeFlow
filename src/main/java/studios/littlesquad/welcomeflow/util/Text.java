package studios.littlesquad.welcomeflow.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Central MiniMessage parsing helper. This is the only place in the
 * codebase that should hold a {@link MiniMessage} instance -- everything
 * else asks {@code Text} to turn a raw config string into a
 * {@link Component}, so every user-facing string in the plugin renders
 * consistently. See the "Adventure / MiniMessage for all user-facing
 * text" hard constraint in PLAN.md.
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String raw) {
        return raw == null || raw.isEmpty() ? Component.empty() : MINI_MESSAGE.deserialize(raw);
    }

    public static Component parse(String raw, TagResolver... resolvers) {
        return raw == null || raw.isEmpty() ? Component.empty() : MINI_MESSAGE.deserialize(raw, resolvers);
    }

    public static TagResolver placeholder(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    public static TagResolver placeholder(String name, Component value) {
        return Placeholder.component(name, value == null ? Component.empty() : value);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
