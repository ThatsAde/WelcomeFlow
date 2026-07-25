package studios.littlesquad.welcomeflow.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Shared tab-completion helpers for subcommands. */
public final class TabCompletions {

    private TabCompletions() {
    }

    public static List<String> onlinePlayerNames(String partial) {
        String lower = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }

    /** Filters any candidate list by a partially-typed prefix. */
    public static List<String> matching(Collection<String> candidates, String partial) {
        String lower = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }
}
