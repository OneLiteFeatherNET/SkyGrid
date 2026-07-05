package net.onelitefeather.skygridng.platforms.olf;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.challenge.NoCraftingTableListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * OLF-only feature: enables the {@code no_crafting_table} challenge enforcement by registering
 * {@link NoCraftingTableListener} with Bukkit's {@link PluginManager} on enable, and unregistering
 * it on disable.
 */
final class ChallengeFeature implements OlfFeature {

    private final JavaPlugin plugin;
    private final NoCraftingTableListener listener;

    @Inject
    ChallengeFeature(JavaPlugin plugin, NoCraftingTableListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    @Override
    public String id() {
        return "challenges";
    }

    @Override
    public void enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(listener, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(listener);
    }
}
