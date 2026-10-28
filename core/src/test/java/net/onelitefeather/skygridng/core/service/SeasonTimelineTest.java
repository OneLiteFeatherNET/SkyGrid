package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonSettings;
import net.onelitefeather.skygridng.ports.out.SeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure season validation and reload rules of {@link SeasonTimeline}. All instants are
 * fixed and the repository is an in memory fake, so no clock, file system or database is touched.
 */
class SeasonTimelineTest {

    private static final Set<String> PRESETS = Set.of("standard", "classic");
    private static final Set<String> CHALLENGES = Set.of("one_life", "no_crafting_table");

    private static final Instant JAN = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant MAR = Instant.parse("2027-03-01T00:00:00Z");
    private static final Duration FOUR_WEEKS = Duration.ofDays(28);

    private static Season season(String id, Instant start, String preset, List<String> challenges) {
        return new Season(new SeasonId(id), id, start, FOUR_WEEKS, preset, challenges, null);
    }

    private static Season season(String id, Instant start, String preset) {
        return season(id, start, preset, List.of("one_life"));
    }

    /** An in memory season repository that counts how often the directory was read. */
    private static final class FakeSeasonRepository implements SeasonRepository {
        private final SeasonCatalog catalog;
        private final AtomicInteger loads = new AtomicInteger();

        FakeSeasonRepository(SeasonCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public SeasonCatalog load() {
            loads.incrementAndGet();
            return catalog;
        }

        @Override
        public SeasonSettings settings() {
            return SeasonSettings.defaults();
        }
    }

    @Test
    @DisplayName("a season naming an unknown preset is rejected with the file id, the value and the known presets")
    void unknownPresetIsRejectedWithKnownValues() {
        SeasonTimeline.Validation result = SeasonTimeline.validate(
                new SeasonCatalog(List.of(season("s01", JAN, "nosuch"))), PRESETS, CHALLENGES);

        assertThat(result.accepted().seasons()).isEmpty();
        assertThat(result.rejections()).singleElement().asString()
                .contains("s01", "nosuch", "standard", "classic");
    }

    @Test
    @DisplayName("a season naming an unknown challenge is rejected with the value and the known challenges")
    void unknownChallengeIsRejectedWithKnownValues() {
        SeasonTimeline.Validation result = SeasonTimeline.validate(
                new SeasonCatalog(List.of(season("s01", JAN, "standard", List.of("bogus")))), PRESETS, CHALLENGES);

        assertThat(result.accepted().seasons()).isEmpty();
        assertThat(result.rejections()).singleElement().asString()
                .contains("s01", "bogus", "one_life", "no_crafting_table");
    }

    @Test
    @DisplayName("an overlapping later season is rejected and the log names both season ids")
    void overlappingLaterSeasonIsRejectedNamingBoth() {
        Season first = season("s01", JAN, "standard");
        Season overlapping = season("s02", JAN.plus(Duration.ofDays(7)), "standard");

        SeasonTimeline.Validation result = SeasonTimeline.validate(
                new SeasonCatalog(List.of(first, overlapping)), PRESETS, CHALLENGES);

        assertThat(result.accepted().seasons()).containsExactly(first);
        assertThat(result.rejections()).singleElement().asString().contains("s01", "s02");
    }

    @Test
    @DisplayName("a season that only starts when the previous one ends does not overlap it")
    void adjacentSeasonsDoNotOverlap() {
        Season first = season("s01", JAN, "standard");
        Season next = season("s02", first.end(), "standard");

        SeasonTimeline.Validation result = SeasonTimeline.validate(
                new SeasonCatalog(List.of(first, next)), PRESETS, CHALLENGES);

        assertThat(result.accepted().seasons()).hasSize(2);
        assertThat(result.rejections()).isEmpty();
    }

    @Test
    @DisplayName("an invalid season does not stop the other valid seasons from loading")
    void otherSeasonsKeepLoading() {
        Season broken = season("s01", JAN, "nosuch");
        Season valid = season("s02", MAR, "classic");

        SeasonTimeline.Validation result = SeasonTimeline.validate(
                new SeasonCatalog(List.of(broken, valid)), PRESETS, CHALLENGES);

        assertThat(result.accepted().seasons()).containsExactly(valid);
        assertThat(result.rejections()).hasSize(1);
    }

    @Test
    @DisplayName("a reload without confirm does not read the seasons directory")
    void reloadWithoutConfirmDoesNotReadDirectory() {
        SeasonCatalog running = new SeasonCatalog(List.of(season("s01", JAN, "standard")));
        FakeSeasonRepository repository = new FakeSeasonRepository(SeasonCatalog.empty());

        SeasonTimeline.Validation result = SeasonTimeline.reload(
                running, repository, PRESETS, CHALLENGES, JAN.plus(Duration.ofDays(1)), false);

        assertThat(repository.loads.get()).isZero();
        assertThat(result.accepted()).isEqualTo(running);
    }

    @Test
    @DisplayName("a confirmed reload keeps the running season unchanged and applies the change to future seasons")
    void confirmedReloadChangesOnlyFutureSeasons() {
        SeasonCatalog running = new SeasonCatalog(List.of(
                season("s01", JAN, "standard"),
                season("s02", MAR, "standard")));
        SeasonCatalog reloaded = new SeasonCatalog(List.of(
                season("s01", JAN, "classic"),
                season("s02", MAR, "classic")));
        FakeSeasonRepository repository = new FakeSeasonRepository(reloaded);

        SeasonTimeline.Validation result = SeasonTimeline.reload(
                running, repository, PRESETS, CHALLENGES, JAN.plus(Duration.ofDays(1)), true);

        assertThat(repository.loads.get()).isEqualTo(1);
        assertThat(result.accepted().seasons()).extracting(Season::preset).containsExactly("standard", "classic");
    }

    @Test
    @DisplayName("an empty catalog runs the implicit legacy season, and no next season is scheduled")
    void emptyCatalogRunsImplicitLegacySeason() {
        SeasonCatalog catalog = SeasonCatalog.empty();

        assertThat(SeasonTimeline.activeId(catalog, JAN)).isEqualTo(SeasonId.LEGACY);
        assertThat(catalog.next(SeasonId.LEGACY)).isEmpty();
    }
}
