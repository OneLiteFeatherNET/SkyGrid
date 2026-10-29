package net.onelitefeather.skygridng.platforms.olf.season;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Runs Bukkit work on the server main thread. On the main thread the work runs at once; from any other thread (the
 * async season tick) it is handed to the scheduler and the caller waits for its result, which is safe because the
 * main thread never waits for the season tick.
 */
final class MainThread {

    private MainThread() {
    }

    static <T> T call(Plugin plugin, Callable<T> work) {
        if (Bukkit.isPrimaryThread()) {
            return invoke(work);
        }
        try {
            return plugin.getServer().getScheduler().callSyncMethod(plugin, work).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the main thread", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    static void run(Plugin plugin, Runnable work) {
        call(plugin, () -> {
            work.run();
            return null;
        });
    }

    private static <T> T invoke(Callable<T> work) {
        try {
            return work.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
