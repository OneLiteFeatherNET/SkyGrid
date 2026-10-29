package net.onelitefeather.skygridng.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link SeasonCatalog}: seasons are kept ordered by start, ids are unique, and
 * the lookups {@code at} and {@code next} follow the start order.
 */
class SeasonCatalogTest {

    private static final Instant JAN = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant MAR = Instant.parse("2027-03-01T00:00:00Z");

    private static Season season(String id, Instant start) {
        return new Season(new SeasonId(id), id, start, Duration.ofDays(28), "standard", List.of(), null);
    }

    @Test
    @DisplayName("seasons are kept ordered by their start instant")
    void seasonsAreOrderedByStart() {
        SeasonCatalog catalog = new SeasonCatalog(List.of(season("s02", MAR), season("s01", JAN)));

        assertThat(catalog.seasons()).extracting(s -> s.id().value()).containsExactly("s01", "s02");
    }

    @Test
    @DisplayName("two seasons with the same id are rejected")
    void duplicateIdIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SeasonCatalog(List.of(season("s01", JAN), season("s01", MAR))));
    }

    @Test
    @DisplayName("at returns the season containing the instant")
    void atReturnsContainingSeason() {
        SeasonCatalog catalog = new SeasonCatalog(List.of(season("s01", JAN), season("s02", MAR)));

        assertThat(catalog.at(MAR.plusSeconds(1))).map(s -> s.id().value()).contains("s02");
    }

    @Test
    @DisplayName("next returns the season that starts after the given id")
    void nextReturnsFollowingSeason() {
        SeasonCatalog catalog = new SeasonCatalog(List.of(season("s01", JAN), season("s02", MAR)));

        assertThat(catalog.next(new SeasonId("s01"))).map(s -> s.id().value()).contains("s02");
    }

    @Test
    @DisplayName("next is empty for the last season, so no transition is scheduled")
    void nextIsEmptyForLastSeason() {
        SeasonCatalog catalog = new SeasonCatalog(List.of(season("s01", JAN), season("s02", MAR)));

        assertThat(catalog.next(new SeasonId("s02"))).isEmpty();
    }

    @Test
    @DisplayName("an empty catalog has no seasons")
    void emptyCatalogHasNoSeasons() {
        assertThat(SeasonCatalog.empty().seasons()).isEmpty();
    }
}
