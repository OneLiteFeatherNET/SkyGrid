package net.onelitefeather.skygridng.platforms.olf.season;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.onelitefeather.skygridng.adapters.command.Messages;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.out.SeasonAnnouncer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * The Bukkit implementation of {@link SeasonAnnouncer}. Every player announcement is sent to each online player on the
 * main thread, in that player's locale, so dates read the way the viewer reads them. Admin alerts go to the server log and
 * to every online player with {@code skygridng.admin}. All wording lives in the message bundles.
 */
public final class BukkitSeasonAnnouncer implements SeasonAnnouncer {

    private static final String ADMIN_PERMISSION = "skygridng.admin";

    private final Plugin plugin;

    public BukkitSeasonAnnouncer(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void upcoming(SeasonId season, Duration remaining, Instant boundary) {
        sendToOnline(player -> Messages.seasonAnnouncementUpcoming(season.value(),
                Messages.seasonDate(boundary, player.locale()), pendingMailbox(player)));
    }

    @Override
    public void started(SeasonId season) {
        sendToOnline(player -> Messages.seasonAnnouncementStarted(season.value(), pendingMailbox(player)));
    }

    @Override
    public void postponed(SeasonId season, Instant newEnd) {
        sendToOnline(player -> List.of(Messages.seasonAnnouncementPostponed(season.value(),
                Messages.seasonDate(newEnd, player.locale()))));
    }

    @Override
    public void adminAlert(String message) {
        plugin.getLogger().warning(message);
        Component alert = Component.text("[SkyGrid] " + message, NamedTextColor.RED);
        MainThread.run(plugin, () -> plugin.getServer().broadcast(alert, ADMIN_PERMISSION));
    }

    /** Sends the lines built for each online player, so the lines carry that player's own locale and mailbox count. */
    private void sendToOnline(Function<Player, List<Component>> lines) {
        MainThread.run(plugin, () -> plugin.getServer().getOnlinePlayers().forEach(player ->
                lines.apply(player).forEach(player::sendMessage)));
    }

    /** The viewer's pending mailbox count. Always 0 until the economy adds pending mailbox items. */
    private static int pendingMailbox(Player player) {
        return 0;
    }
}
