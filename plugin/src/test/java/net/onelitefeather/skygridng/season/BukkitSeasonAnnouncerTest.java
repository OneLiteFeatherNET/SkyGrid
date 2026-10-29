package net.onelitefeather.skygridng.season;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the season announcements. Lines are checked by their translation keys, so the tests do not depend
 * on the English wording, and the scheduler is not involved.
 */
class BukkitSeasonAnnouncerTest {

    private static final SeasonId SEASON = new SeasonId("s01");
    private static final Instant BOUNDARY = Instant.parse("2027-01-29T00:00:00Z");
    private static final String DELETED = "skygridng.command.season.reset.deleted";
    private static final String KEPT = "skygridng.command.season.reset.kept";

    private ServerMock server;
    private BukkitSeasonAnnouncer announcer;
    private PlayerMock player;
    private SeasonCatalog catalog = SeasonCatalog.empty();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin("SkyGridNg");
        announcer = new BukkitSeasonAnnouncer(plugin, () -> catalog);
        player = server.addPlayer();
    }

    /** A catalog holding the test season under {@code displayName}. */
    private void catalogNaming(String displayName) {
        catalog = new SeasonCatalog(List.of(new Season(SEASON, displayName, BOUNDARY.minus(Duration.ofDays(28)),
                Duration.ofDays(28), "standard", List.of(), null)));
    }

    /** Every argument of every translatable component of every line the player received. */
    private List<Component> receivedArguments() {
        List<Component> arguments = new ArrayList<>();
        for (Component line = player.nextComponentMessage(); line != null; line = player.nextComponentMessage()) {
            collectArguments(line, arguments);
        }
        return arguments;
    }

    private static void collectArguments(Component component, List<Component> arguments) {
        if (component instanceof TranslatableComponent translatable) {
            arguments.addAll(translatable.args());
        }
        component.children().forEach(child -> collectArguments(child, arguments));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Every translation key of every line the player received. */
    private List<String> receivedKeys() {
        List<String> keys = new ArrayList<>();
        for (Component line = player.nextComponentMessage(); line != null; line = player.nextComponentMessage()) {
            collectKeys(line, keys);
        }
        return keys;
    }

    private static void collectKeys(Component component, List<String> keys) {
        if (component instanceof TranslatableComponent translatable) {
            keys.add(translatable.key());
        }
        component.children().forEach(child -> collectKeys(child, keys));
    }

    @Test
    void upcomingAnnouncementNamesTheDeletedAndTheKeptLists() {
        announcer.upcoming(SEASON, Duration.ofDays(7), BOUNDARY);

        assertThat(receivedKeys())
                .as("a season end announcement names what is deleted and what is kept")
                .contains(DELETED, KEPT);
    }

    @Test
    void startAnnouncementNamesTheDeletedAndTheKeptLists() {
        announcer.started(SEASON);

        assertThat(receivedKeys())
                .as("the season start announcement names what is deleted and what is kept")
                .contains(DELETED, KEPT);
    }

    @Test
    void upcomingAnnouncementNamesTheSeasonByItsDisplayName() {
        catalogNaming("<gold>Season 1</gold>");

        announcer.upcoming(SEASON, Duration.ofDays(7), BOUNDARY);

        assertThat(receivedArguments())
                .as("the upcoming notice shows the display name parsed as MiniMessage, not the raw id")
                .contains(MiniMessage.miniMessage().deserialize("<gold>Season 1</gold>"));
    }

    @Test
    void upcomingAnnouncementForAnIncomingSeasonNamesTheIncomingSeason() {
        SeasonId incoming = new SeasonId("s02");
        catalog = new SeasonCatalog(List.of(
                new Season(SEASON, "<gold>Season 1</gold>", BOUNDARY.minus(Duration.ofDays(28)),
                        Duration.ofDays(28), "standard", List.of(), null),
                new Season(incoming, "<gold>Season 2</gold>", BOUNDARY, Duration.ofDays(28), "standard", List.of(),
                        null)));

        announcer.upcoming(incoming, Duration.ofDays(7), BOUNDARY);

        assertThat(receivedArguments())
                .as("the notice for the incoming season names the incoming season, not the one ending")
                .contains(MiniMessage.miniMessage().deserialize("<gold>Season 2</gold>"))
                .doesNotContain(MiniMessage.miniMessage().deserialize("<gold>Season 1</gold>"));
    }

    @Test
    void startAnnouncementNamesTheSeasonByItsDisplayName() {
        catalogNaming("<gold>Season 1</gold>");

        announcer.started(SEASON);

        assertThat(receivedArguments())
                .as("the start notice shows the display name parsed as MiniMessage")
                .contains(MiniMessage.miniMessage().deserialize("<gold>Season 1</gold>"));
    }

    @Test
    void postponedAnnouncementNamesTheSeasonByItsDisplayName() {
        catalogNaming("<gold>Season 1</gold>");

        announcer.postponed(SEASON, BOUNDARY);

        assertThat(receivedArguments())
                .as("the postponement notice shows the display name parsed as MiniMessage")
                .contains(MiniMessage.miniMessage().deserialize("<gold>Season 1</gold>"));
    }

    @Test
    void announcementFallsBackToTheIdWhenTheSeasonIsNotInTheCatalog() {
        announcer.started(SEASON);

        assertThat(receivedArguments())
                .as("a season that is not in the catalog, such as the legacy season, is named by its id")
                .contains(MiniMessage.miniMessage().deserialize(SEASON.value()));
    }

    @Test
    void announcementsAddNoScoreboardOrBossBarCountdown() {
        announcer.upcoming(SEASON, Duration.ofDays(7), BOUNDARY);
        server.getScheduler().performTicks(40);

        assertThat(player.getScoreboard().getObjectives())
                .as("no sidebar countdown is rendered during a running season")
                .isEmpty();
    }
}
