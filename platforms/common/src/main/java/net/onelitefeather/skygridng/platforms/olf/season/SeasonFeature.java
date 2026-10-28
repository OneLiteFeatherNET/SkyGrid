package net.onelitefeather.skygridng.platforms.olf.season;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.platforms.common.DatabaseWarmUpGate;
import net.onelitefeather.skygridng.platforms.olf.OlfFeature;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.ports.in.SeasonAdminUseCase;
import net.onelitefeather.skygridng.ports.out.PlayerSeasonReset;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;

/**
 * OLF-only feature: the season lifecycle on the Bukkit side. It registers the join listener on enable, and starts the
 * season ticker on {@link ServerLoadEvent}, never in {@code onEnable}, so the server has loaded its worlds first. With
 * no season configured the ticker never starts, so the server behaves exactly as a server without seasons.
 */
public final class SeasonFeature implements OlfFeature, Listener {

    private final JavaPlugin plugin;
    private final SeasonAdminUseCase seasons;
    private final CurrentSeason current;
    private final PlayerSeasonReset resets;
    private final Clock clock;
    private final DatabaseWarmUpGate gate;
    private SeasonJoinListener joins;
    private SeasonTicker ticker;
    private boolean started;

    @Inject
    public SeasonFeature(JavaPlugin plugin, SeasonAdminUseCase seasons, CurrentSeason current,
                         PlayerSeasonReset resets, Clock clock, DatabaseWarmUpGate gate) {
        this.plugin = plugin;
        this.seasons = seasons;
        this.current = current;
        this.resets = resets;
        this.clock = clock;
        this.gate = gate;
    }

    @Override
    public String id() {
        return "seasons";
    }

    @Override
    public void enable() {
        joins = new SeasonJoinListener(plugin, current, resets, clock);
        ticker = new SeasonTicker(plugin, seasons, clock, gate);
        plugin.getServer().getPluginManager().registerEvents(joins, plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        if (ticker != null) {
            ticker.stop();
        }
        HandlerList.unregisterAll(this);
        if (joins != null) {
            HandlerList.unregisterAll(joins);
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (started || seasons.list().seasons().isEmpty()) {
            return;
        }
        started = true;
        ticker.start();
    }
}
