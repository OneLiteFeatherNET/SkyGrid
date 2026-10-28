package net.onelitefeather.skygridng.platforms.olf.season;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.ports.in.SeasonAdminUseCase;
import net.onelitefeather.skygridng.ports.out.PlayerSeasonReset;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes a joining player into the active season world and applies the once per season reset. On the spawn event the
 * player is moved when the spawn world is not the active season world. On the join the player is routed and reset as
 * soon as the active season and its world are known; a player who joins during the restart window, before the season
 * state is read or the world is loaded, waits in the world the server gave them and is routed on a later check. In the
 * last seven days of the season, a single line names the end date and what the next season deletes.
 */
public final class SeasonJoinListener implements Listener {

    private static final Duration FINAL_WEEK = Duration.ofDays(7);
    private static final long WAIT_PERIOD_TICKS = 10L;
    private static final DateTimeFormatter END_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private final CurrentSeason current;
    private final SeasonAdminUseCase seasons;
    private final PlayerSeasonReset resets;
    private final Clock clock;
    private final Map<UUID, BukkitTask> waiting = new ConcurrentHashMap<>();

    public SeasonJoinListener(Plugin plugin, CurrentSeason current, SeasonAdminUseCase seasons,
                              PlayerSeasonReset resets, Clock clock) {
        this.plugin = plugin;
        this.current = current;
        this.seasons = seasons;
        this.resets = resets;
        this.clock = clock;
    }

    @EventHandler
    public void onSpawn(PlayerSpawnLocationEvent event) {
        Optional<Season> season = current.season();
        if (season.isEmpty()) {
            return;
        }
        World active = activeWorld(season.get());
        if (active != null && !active.equals(event.getSpawnLocation().getWorld())) {
            event.setSpawnLocation(active.getSpawnLocation());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (seasons.list().seasons().isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (routeIfKnown(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (routeIfKnown(player)) {
                stopWaiting(id);
            }
        }, WAIT_PERIOD_TICKS, WAIT_PERIOD_TICKS);
        waiting.put(id, task);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopWaiting(event.getPlayer().getUniqueId());
    }

    private void stopWaiting(UUID id) {
        BukkitTask task = waiting.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /** Moves the player into the active season and applies the reset, when the season and its world are known. */
    private boolean routeIfKnown(Player player) {
        Optional<Season> season = current.season();
        if (season.isEmpty()) {
            return false;
        }
        World active = activeWorld(season.get());
        if (active == null) {
            return false;
        }
        if (!active.equals(player.getWorld())) {
            player.teleport(active.getSpawnLocation());
        }
        resets.reset(player.getUniqueId(), season.get().id());
        Duration left = Duration.between(clock.instant(), season.get().end());
        if (left.isPositive() && left.compareTo(FINAL_WEEK) <= 0) {
            player.sendMessage(finalWeekLine(season.get()));
        }
        return true;
    }

    private World activeWorld(Season season) {
        return plugin.getServer().getWorld(NamespacedKey.fromString(SeasonWorldKeys.worldKey(season.id())));
    }

    private static Component finalWeekLine(Season season) {
        return Component.text("Season " + season.id().value() + " ends " + END_DATE.format(season.end())
                + ". At the next season your inventory, ender chest, experience, health, hunger, effects, respawn"
                + " point, game mode, tiers and task progress are deleted. Your season history is kept.",
                NamedTextColor.YELLOW);
    }
}
