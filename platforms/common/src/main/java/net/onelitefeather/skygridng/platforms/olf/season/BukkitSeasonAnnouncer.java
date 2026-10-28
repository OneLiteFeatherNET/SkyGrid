package net.onelitefeather.skygridng.platforms.olf.season;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.out.SeasonAnnouncer;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;

/**
 * The Bukkit implementation of {@link SeasonAnnouncer}: broadcasts on the main thread, and sends admin alerts to the
 * server log and to every online player with {@code skygridng.admin}.
 */
public final class BukkitSeasonAnnouncer implements SeasonAnnouncer {

    private static final String ADMIN_PERMISSION = "skygridng.admin";

    private final Plugin plugin;

    public BukkitSeasonAnnouncer(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void upcoming(SeasonId season, Duration remaining, Instant boundary) {
        broadcast(Component.text("Season " + season.value() + " starts in " + remaining.toHours() + " hours.",
                NamedTextColor.GOLD));
    }

    @Override
    public void started(SeasonId season) {
        broadcast(Component.text("Season " + season.value() + " has started.", NamedTextColor.GREEN));
    }

    @Override
    public void postponed(SeasonId season, Instant newEnd) {
        broadcast(Component.text("Season " + season.value() + " now ends at " + newEnd + ".",
                NamedTextColor.YELLOW));
    }

    @Override
    public void adminAlert(String message) {
        plugin.getLogger().warning(message);
        Component alert = Component.text("[SkyGrid] " + message, NamedTextColor.RED);
        MainThread.run(plugin, () -> plugin.getServer().broadcast(alert, ADMIN_PERMISSION));
    }

    private void broadcast(Component message) {
        MainThread.run(plugin, () -> plugin.getServer().broadcast(message));
    }
}
