package studios.littlesquad.welcomeflow.compat;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command dispatch via {@link Bukkit#dispatchCommand(CommandSender, String)}.
 *
 * <p>{@code Player#performCommand(String)} was removed in paper-api 26.x.
 * {@code Bukkit.dispatchCommand} is present -- and behaves the same -- in
 * both 1.21.4 and 26.1.2, so it is the only way this plugin runs a
 * command as a player or as console. Enforced by {@code compileVerify26Java}
 * (see build.gradle.kts).
 */
public final class CommandDispatch {

    private CommandDispatch() {
    }

    /**
     * Runs {@code command} as the given player. {@code command} must not
     * include the leading slash.
     */
    public static boolean asPlayer(Player player, String command) {
        return Bukkit.dispatchCommand(player, command);
    }

    /**
     * Runs {@code command} as the console. {@code command} must not
     * include the leading slash.
     */
    public static boolean asConsole(String command) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
