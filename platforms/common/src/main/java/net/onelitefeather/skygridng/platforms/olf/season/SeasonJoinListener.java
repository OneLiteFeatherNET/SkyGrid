package net.onelitefeather.skygridng.platforms.olf.season;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.ports.out.PlayerSeasonReset;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Routes a joining player into the active season world and applies the once per season reset. On the spawn event the
 * player is moved when the spawn world is not the active season world; on the join the season reset runs and, in the
 * last seven days of the season, a single line names the end date and what the next season deletes.
 */
public final class SeasonJoinListener implements Listener {

    private static final Duration FINAL_WEEK = Duration.ofDays(7);
    private static final DateTimeFormatter END_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private final CurrentSeason current;
    private final PlayerSeasonReset resets;
    private final Clock clock;

    public SeasonJoinListener(Plugin plugin, CurrentSeason current, PlayerSeasonReset resets, Clock clock) {
        this.plugin = plugin;
        this.current = current;
        this.resets = resets;
        this.clock = clock;
    }

    @EventHandler
    public void onSpawn(PlayerSpawnLocationEvent event) {
        Optional<Season> season = current.season();
        if (season.isEmpty()) {
            return;
        }
        World active = plugin.getServer().getWorld(NamespacedKey.fromString(
                SeasonWorldKeys.worldKey(season.get().id())));
        if (active != null && !active.equals(event.getSpawnLocation().getWorld())) {
            event.setSpawnLocation(active.getSpawnLocation());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Optional<Season> season = current.season();
        if (season.isEmpty()) {
            return;
        }
        resets.reset(event.getPlayer().getUniqueId(), season.get().id());
        Duration left = Duration.between(clock.instant(), season.get().end());
        if (left.isPositive() && left.compareTo(FINAL_WEEK) <= 0) {
            event.getPlayer().sendMessage(finalWeekLine(season.get()));
        }
    }

    private static Component finalWeekLine(Season season) {
        return Component.text("Season " + season.id().value() + " ends " + END_DATE.format(season.end())
                + ". At the next season your inventory, ender chest, experience, health, hunger, effects, respawn"
                + " point, game mode, tiers and task progress are deleted. Your season history is kept.",
                NamedTextColor.YELLOW);
    }
}
