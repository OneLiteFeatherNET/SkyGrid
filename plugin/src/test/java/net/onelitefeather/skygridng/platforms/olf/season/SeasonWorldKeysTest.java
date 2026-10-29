package net.onelitefeather.skygridng.platforms.olf.season;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the scope of the challenge logic: only the world of the active season is in scope, so a prepared world that
 * is not active yet never triggers a challenge.
 */
class SeasonWorldKeysTest {

    private static final Season ACTIVE = new Season(new SeasonId("s01"), "Season 1",
            Instant.parse("2027-01-01T00:00:00Z"), Duration.ofDays(28), "standard", List.of(), null);

    private static CurrentSeason current(Optional<Season> season) {
        return new CurrentSeason() {
            @Override
            public SeasonId id() {
                return season.map(Season::id).orElse(SeasonId.LEGACY);
            }

            @Override
            public Optional<Season> season() {
                return season;
            }
        };
    }

    private static World worldKeyed(String key) {
        World world = mock(World.class);
        when(world.getKey()).thenReturn(NamespacedKey.fromString(key));
        return world;
    }

    @Test
    void theWorldOfTheActiveSeasonIsInScope() {
        assertThat(SeasonWorldKeys.isActive(worldKeyed("skygridng:season_s01"), current(Optional.of(ACTIVE))))
                .as("the active season world triggers challenge logic")
                .isTrue();
    }

    @Test
    void aPreparedWorldOfTheNextSeasonIsOutOfScopeWhileAnotherSeasonIsActive() {
        assertThat(SeasonWorldKeys.isActive(worldKeyed("skygridng:season_s02"), current(Optional.of(ACTIVE))))
                .as("a prepared but not yet active season world never triggers challenge logic")
                .isFalse();
    }

    @Test
    void theLegacyWorldIsOutOfScopeWhileASeasonIsActive() {
        assertThat(SeasonWorldKeys.isActive(worldKeyed("world"), current(Optional.of(ACTIVE))))
                .as("the old level world is not the active season world once a season runs")
                .isFalse();
    }

    @Test
    void everyWorldIsInScopeWhenNoSeasonIsConfigured() {
        assertThat(SeasonWorldKeys.isActive(worldKeyed("world"), current(Optional.empty())))
                .as("without seasons the scope is unchanged: every SkyGrid world counts")
                .isTrue();
    }
}
