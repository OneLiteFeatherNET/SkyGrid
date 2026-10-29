package net.onelitefeather.skygridng.platforms.olf;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.generator.SkyGridApi;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reference feature: on enable it logs an OLF banner with the active generator state read through the
 * public {@link SkyGridApi}, and does nothing on disable. It exists to prove the feature seam end to
 * end and to serve as the template for real mechanics (challenges, ...). Real features will register
 * listeners, commands, or tasks in {@link #enable()} and release them in {@link #disable()}.
 */
final class NetworkStartupReportFeature implements OlfFeature {

    private final JavaPlugin plugin;
    private final SkyGridApi api;

    @Inject
    NetworkStartupReportFeature(JavaPlugin plugin, SkyGridApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public String id() {
        return "network-startup-report";
    }

    @Override
    public void enable() {
        plugin.getLogger().info("[OLF] Network distribution active. Algorithm: " + api.algorithm()
                + ", biomes: " + api.configuration().biomes().size() + ", api " + api.apiVersion() + ".");
    }

    @Override
    public void disable() {
    }
}
