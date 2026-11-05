package studios.littlesquad.welcomeflow.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Thin wrapper around {@link org.bukkit.scheduler.BukkitScheduler} with
 * intention-revealing names, so call sites read as "this must run on the
 * main thread" or "this runs off it" instead of bare runTask calls. The
 * rule is: never call the Bukkit API off the main thread, and never touch
 * the database on it.
 */
public final class Scheduling {

    private final Plugin plugin;

    public Scheduling(Plugin plugin) {
        this.plugin = plugin;
    }

    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runSyncLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public BukkitTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    public BukkitTask runSyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    /**
     * Runs {@code supplier} off the main thread (safe for DB/IO work),
     * then hands its result back to {@code onMainThread} on the main
     * thread (safe for Bukkit API calls).
     */
    public <T> void supplyAsync(Supplier<T> supplier, Consumer<T> onMainThread) {
        runAsync(() -> {
            T result = supplier.get();
            runSync(() -> onMainThread.accept(result));
        });
    }
}
