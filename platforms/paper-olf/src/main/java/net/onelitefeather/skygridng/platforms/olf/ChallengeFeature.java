package net.onelitefeather.skygridng.platforms.olf;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.challenge.LevelBorderWarningTask;
import net.onelitefeather.skygridng.adapters.challenge.NoCraftingTableListener;
import net.onelitefeather.skygridng.adapters.challenge.NoNaturalRegenerationListener;
import net.onelitefeather.skygridng.adapters.challenge.OneLifeListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * OLF-only feature: enables all challenge enforcement by registering
 * {@link NoCraftingTableListener}, {@link OneLifeListener}, and {@link NoNaturalRegenerationListener}
 * with Bukkit's {@link PluginManager} on enable, and scheduling {@link LevelBorderWarningTask} to run
 * every 5 seconds (100 ticks). Everything is unregistered / cancelled on disable.
 */
final class ChallengeFeature implements OlfFeature {

    private static final long LEVEL_BORDER_WARNING_PERIOD_TICKS = 100L;

    private final JavaPlugin plugin;
    private final NoCraftingTableListener noCraftingTableListener;
    private final OneLifeListener oneLifeListener;
    private final NoNaturalRegenerationListener noNaturalRegenerationListener;
    private final LevelBorderWarningTask levelBorderWarningTask;

    private BukkitTask levelBorderWarningBukkitTask;

    @Inject
    ChallengeFeature(JavaPlugin plugin,
                      NoCraftingTableListener noCraftingTableListener,
                      OneLifeListener oneLifeListener,
                      NoNaturalRegenerationListener noNaturalRegenerationListener,
                      LevelBorderWarningTask levelBorderWarningTask) {
        this.plugin = plugin;
        this.noCraftingTableListener = noCraftingTableListener;
        this.oneLifeListener = oneLifeListener;
        this.noNaturalRegenerationListener = noNaturalRegenerationListener;
        this.levelBorderWarningTask = levelBorderWarningTask;
    }

    @Override
    public String id() {
        return "challenges";
    }

    @Override
    public void enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(noCraftingTableListener, plugin);
        pluginManager.registerEvents(oneLifeListener, plugin);
        pluginManager.registerEvents(noNaturalRegenerationListener, plugin);
        levelBorderWarningBukkitTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, levelBorderWarningTask, 0L, LEVEL_BORDER_WARNING_PERIOD_TICKS);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(noCraftingTableListener);
        HandlerList.unregisterAll(oneLifeListener);
        HandlerList.unregisterAll(noNaturalRegenerationListener);
        if (levelBorderWarningBukkitTask != null) {
            levelBorderWarningBukkitTask.cancel();
        }
    }
}
