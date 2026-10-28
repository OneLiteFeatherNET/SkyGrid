package net.onelitefeather.skygridng.platforms.olf.season;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the season join routing and the final week line. The listener is driven with the events
 * directly, so the tests do not depend on the server's login sequence. The clock is fixed.
 */
class SeasonJoinListenerTest {

    private static final Instant START = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant END = START.plus(Duration.ofDays(28));
    private static final String ACTIVE = "skygridng:season_s01";

    private ServerMock server;
    private Plugin plugin;
    private World activeWorld;
    private Season season;
    private final List<UUID> resets = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
        activeWorld = server.createWorld(WorldCreator.ofKey(NamespacedKey.fromString(ACTIVE)));
        season = new Season(new SeasonId("s01"), "Season 1", START, Duration.ofDays(28), "standard", List.of(), null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SeasonJoinListener listener(Instant now) {
        CurrentSeason current = new CurrentSeason() {
            @Override
            public SeasonId id() {
                return season.id();
            }

            @Override
            public Optional<Season> season() {
                return Optional.of(season);
            }
        };
        return new SeasonJoinListener(plugin, current, (player, seasonId) -> resets.add(player), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void playerSpawningOutsideTheActiveSeasonWorldIsRoutedIntoIt() {
        PlayerMock player = server.addPlayer();
        World other = server.addSimpleWorld("lobby");
        var event = new PlayerSpawnLocationEvent(player, other.getSpawnLocation());

        listener(START.plus(Duration.ofDays(1))).onSpawn(event);

        assertThat(event.getSpawnLocation().getWorld())
                .as("a player whose spawn world is not the active season world is routed into it")
                .isEqualTo(activeWorld);
    }

    @Test
    void playerAlreadyInTheActiveSeasonWorldIsNotMoved() {
        PlayerMock player = server.addPlayer();
        Location spawn = activeWorld.getSpawnLocation();
        var event = new PlayerSpawnLocationEvent(player, spawn);

        listener(START.plus(Duration.ofDays(1))).onSpawn(event);

        assertThat(event.getSpawnLocation())
                .as("a player already in the active season world keeps the location it was given")
                .isEqualTo(spawn);
    }

    @Test
    void joiningResetsThePlayerForTheCurrentSeason() {
        PlayerMock player = server.addPlayer();

        listener(START.plus(Duration.ofDays(1))).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(resets)
                .as("the join hands the player to the season reset, which decides whether a reset is due")
                .containsExactly(player.getUniqueId());
    }

    @Test
    void joinThreeDaysBeforeTheSeasonEndSendsOneFinalWeekLineWithTheEndDate() {
        PlayerMock player = server.addPlayer();

        listener(END.minus(Duration.ofDays(3))).onJoin(new PlayerJoinEvent(player, Component.empty()));

        Component line = player.nextComponentMessage();
        assertThat(line)
                .as("a player joining in the last week gets the final week line")
                .isNotNull();
        assertThat(PlainTextComponentSerializer.plainText().serialize(line))
                .as("the final week line names the absolute end date")
                .contains("2027-01-29");
        assertThat(player.nextComponentMessage())
                .as("the final week line is sent exactly once per join")
                .isNull();
    }

    @Test
    void joinTenDaysBeforeTheSeasonEndSendsNoFinalWeekLine() {
        PlayerMock player = server.addPlayer();

        listener(END.minus(Duration.ofDays(10))).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(player.nextComponentMessage())
                .as("a player joining more than a week before the end gets no final week line")
                .isNull();
    }
}
