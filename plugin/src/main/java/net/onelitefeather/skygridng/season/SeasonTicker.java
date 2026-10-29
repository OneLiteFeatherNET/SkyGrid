package net.onelitefeather.skygridng.season;

import net.onelitefeather.skygridng.persistence.DatabaseWarmUpGate;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;

/**
 * The season scheduler: every 600 ticks (30 seconds) it hands the current instant to the season service. It holds no
 * logic of its own. The database work runs on an async task, and nothing runs before the database warm-up has finished
 * or failed. Each run repeats the step while the service reports one, up to a fixed bound, so a long transition
 * finishes without monopolising the async thread.
 */
public final class SeasonTicker implements Runnable {

    static final long PERIOD_TICKS = 600L;
    static final int MAX_STEPS_PER_RUN = 20;

    private final Plugin plugin;
    private final SeasonAdminUseCase seasons;
    private final Clock clock;
    private final DatabaseWarmUpGate gate;
    private BukkitTask task;

    public SeasonTicker(Plugin plugin, SeasonAdminUseCase seasons, Clock clock, DatabaseWarmUpGate gate) {
        this.plugin = plugin;
        this.seasons = seasons;
        this.clock = clock;
        this.gate = gate;
    }

    /** Starts the schedule; the first run is on the next tick, and every {@link #PERIOD_TICKS} after it. */
    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 0L, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        if (!gate.isWarmedUp()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::drain);
    }

    private void drain() {
        for (int step = 0; step < MAX_STEPS_PER_RUN && seasons.tick(clock.instant()); step++) {
            // each call performs at most one due step; the loop bound is the whole policy
        }
    }
}
