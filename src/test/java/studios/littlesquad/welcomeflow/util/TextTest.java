package studios.littlesquad.welcomeflow.util;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TextTest {

    @Test
    void parsesMiniMessageAndStripsToPlainText() {
        Component component = Text.parse("<green>Hello <white>World");
        assertNotNull(component);
        assertEquals("Hello World", Text.plain(component));
    }

    @Test
    void emptyOrNullInputYieldsEmptyComponent() {
        assertEquals("", Text.plain(Text.parse(null)));
        assertEquals("", Text.plain(Text.parse("")));
    }

    @Test
    void placeholderResolvesUnparsedValue() {
        Component component = Text.parse("Hello <name>", Text.placeholder("name", "Steve"));
        assertEquals("Hello Steve", Text.plain(component));
    }
}
