package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonPhase;
import net.onelitefeather.skygridng.api.model.SeasonRun;
import net.onelitefeather.skygridng.api.model.SeasonSettings;
import net.onelitefeather.skygridng.ports.out.SeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.onelitefeather.skygridng.api.model.SeasonPhase.ACTIVE;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PLANNED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.CLOSING;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARE_FAILED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARING;
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
    private static final Duration DAY = Duration.ofDays(1);

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

    private static final SeasonSettings SETTINGS = SeasonSettings.defaults();
    private static final Season A = season("s01", JAN, "standard");
    private static final Season B = season("s02", JAN.plus(FOUR_WEEKS), "classic");
    private static final Instant BOUNDARY = B.start();
    private static final SeasonCatalog PAIR = new SeasonCatalog(List.of(A, B));

    private static SeasonRun run(String id, SeasonPhase phase) {
        return new SeasonRun(new SeasonId(id), "skygridng:season_" + id, 42L, phase, 0, null, null);
    }

    private static SeasonAction.Announce announce(SeasonAction.AnnouncementKind kind) {
        return new SeasonAction.Announce(B.id(), kind, BOUNDARY, BOUNDARY.minus(kind.offset()));
    }

    private static Set<SeasonAction.Announce> allAnnouncedFor(Season season) {
        return Arrays.stream(SeasonAction.AnnouncementKind.values())
                .map(kind -> new SeasonAction.Announce(season.id(), kind, season.start(),
                        season.start().minus(kind.offset())))
                .collect(Collectors.toSet());
    }

    private static Set<SeasonAction.Announce> allAnnounced() {
        return Set.of(announce(SeasonAction.AnnouncementKind.SEVEN_DAYS),
                announce(SeasonAction.AnnouncementKind.ONE_DAY),
                announce(SeasonAction.AnnouncementKind.ONE_HOUR),
                announce(SeasonAction.AnnouncementKind.FIVE_MINUTES));
    }

    @Test
    void switchesAtTheEndInstantWhenTheNextSeasonIsPrepared() {
        var action = SeasonTimeline.nextAction(BOUNDARY, PAIR, SETTINGS,
                List.of(run("s01", ACTIVE), run("s02", PREPARED)), allAnnounced(), Optional.empty());

        assertThat(action)
                .as("a prepared next season must switch at the end instant of the active season")
                .isEqualTo(new SeasonAction.Switch(A.id(), B.id(), BOUNDARY));
    }

    @Test
    void preparesTheNextWorldOneLeadBeforeTheBoundary() {
        var action = SeasonTimeline.nextAction(BOUNDARY.minus(Duration.ofHours(1)), PAIR, SETTINGS,
                List.of(run("s01", ACTIVE)), allAnnounced(), Optional.empty());

        assertThat(action)
                .as("the next world is prepared the default 15 minutes before the boundary")
                .isEqualTo(new SeasonAction.Prepare(B, BOUNDARY.minus(Duration.ofMinutes(15))));
    }

    @Test
    void announcesSevenDaysBeforeTheBoundaryFirst() {
        var action = SeasonTimeline.nextAction(BOUNDARY.minus(Duration.ofDays(8)), PAIR, SETTINGS,
                List.of(run("s01", ACTIVE)), Set.of(), Optional.empty());

        assertThat(action)
                .as("the first notice is seven days before the boundary")
                .isEqualTo(announce(SeasonAction.AnnouncementKind.SEVEN_DAYS));
    }

    @Test
    void returnsTheNextNoticeOnceTheEarlierOneIsRecorded() {
        var action = SeasonTimeline.nextAction(BOUNDARY.minus(Duration.ofDays(8)), PAIR, SETTINGS,
                List.of(run("s01", ACTIVE)), Set.of(announce(SeasonAction.AnnouncementKind.SEVEN_DAYS)),
                Optional.empty());

        assertThat(action)
                .as("a notice that was already sent is never returned again")
                .isEqualTo(announce(SeasonAction.AnnouncementKind.ONE_DAY));
    }

    @Test
    void returnsNoNoticeOnceAllFourAreRecorded() {
        var action = SeasonTimeline.nextAction(BOUNDARY.minus(Duration.ofDays(8)), PAIR, SETTINGS,
                List.of(run("s01", ACTIVE)), allAnnounced(), Optional.empty());

        assertThat(action)
                .as("with every notice sent, the next action is the preparation, not another notice")
                .isEqualTo(new SeasonAction.Prepare(B, BOUNDARY.minus(Duration.ofMinutes(15))));
    }

    @Test
    void resumesAnInFlightRunBeforeAnythingElse() {
        var resumed = run("s02", PREPARING);

        var action = SeasonTimeline.nextAction(BOUNDARY.minus(Duration.ofDays(8)), PAIR, SETTINGS,
                List.of(run("s01", ACTIVE), resumed), Set.of(), Optional.empty());

        assertThat(action)
                .as("an in flight preparation is resumed before any notice is sent")
                .isEqualTo(new SeasonAction.Resume(resumed));
    }

    @Test
    void resumesAClosingRunOfTheEndingSeason() {
        var closing = run("s01", CLOSING);

        var action = SeasonTimeline.nextAction(BOUNDARY, PAIR, SETTINGS,
                List.of(closing, run("s02", ACTIVE)), allAnnounced(), Optional.empty());

        assertThat(action)
                .as("a closing season is resumed at its unload and archive steps")
                .isEqualTo(new SeasonAction.Resume(closing));
    }

    @Test
    void doesNotResumeAFailedPreparationAutomatically() {
        var action = SeasonTimeline.nextAction(BOUNDARY, PAIR, SETTINGS,
                List.of(run("s01", ACTIVE), run("s02", PREPARE_FAILED)), allAnnounced(), Optional.empty());

        assertThat(action)
                .as("a failed preparation waits for an admin retry, the active season keeps running")
                .isEqualTo(new SeasonAction.None());
    }

    @Test
    void givesNoActionWithoutAnyConfiguredSeason() {
        var action = SeasonTimeline.nextAction(BOUNDARY, SeasonCatalog.empty(), SETTINGS, List.of(), Set.of(),
                Optional.empty());

        assertThat(action)
                .as("with no configured season there is nothing to schedule")
                .isEqualTo(new SeasonAction.None());
    }

    @Test
    void givesNoActionOnceEverySeasonHasEnded() {
        var action = SeasonTimeline.nextAction(B.end().plus(DAY), PAIR, SETTINGS, List.of(), Set.of(),
                Optional.empty());

        assertThat(action)
                .as("a server that starts after the last season ended schedules nothing")
                .isEqualTo(new SeasonAction.None());
    }

    @Test
    void announcesTheFirstSeasonFromLegacyWithoutAnActiveRun() {
        var action = SeasonTimeline.nextAction(JAN.minus(Duration.ofDays(8)), PAIR, SETTINGS, List.of(),
                Set.of(), Optional.empty());

        assertThat(action)
                .as("with no active run the implicit legacy season hands over to the first season, announced from its start")
                .isEqualTo(new SeasonAction.Announce(A.id(), SeasonAction.AnnouncementKind.SEVEN_DAYS, JAN,
                        JAN.minus(Duration.ofDays(7))));
    }

    @Test
    void announcesOnlyTheNoticesThatAreStillAheadWhenTheServerStartsLate() {
        var action = SeasonTimeline.nextAction(JAN.minus(Duration.ofHours(1)), PAIR, SETTINGS, List.of(),
                Set.of(), Optional.empty());

        assertThat(action)
                .as("a server that starts an hour before the first season skips the seven day and one day notices")
                .isEqualTo(new SeasonAction.Announce(A.id(), SeasonAction.AnnouncementKind.ONE_HOUR, JAN,
                        JAN.minus(Duration.ofHours(1))));
    }

    @Test
    void preparesTheFirstSeasonOneLeadBeforeItsStartFromLegacy() {
        var action = SeasonTimeline.nextAction(JAN.minus(Duration.ofHours(1)), PAIR, SETTINGS, List.of(),
                allAnnouncedFor(A), Optional.empty());

        assertThat(action)
                .as("the first season's world is prepared the default 15 minutes before its start")
                .isEqualTo(new SeasonAction.Prepare(A, JAN.minus(Duration.ofMinutes(15))));
    }

    @Test
    void preparesAPlannedFirstSeasonWhoseNoticesAreRecorded() {
        var action = SeasonTimeline.nextAction(JAN.minus(Duration.ofHours(1)), PAIR, SETTINGS,
                List.of(run("s01", PLANNED)), allAnnouncedFor(A), Optional.empty());

        assertThat(action)
                .as("a planned run that only holds the sent notices is still prepared")
                .isEqualTo(new SeasonAction.Prepare(A, JAN.minus(Duration.ofMinutes(15))));
    }

    @Test
    void switchesFromLegacyAtTheFirstSeasonStart() {
        var action = SeasonTimeline.nextAction(JAN, PAIR, SETTINGS, List.of(run("s01", PREPARED)),
                allAnnouncedFor(A), Optional.empty());

        assertThat(action)
                .as("a prepared first season switches from legacy at its start instant")
                .isEqualTo(new SeasonAction.Switch(SeasonId.LEGACY, A.id(), JAN));
    }

    @Test
    void preparesThenSwitchesAtOnceWhenTheServerStartsInsideTheFirstSeason() {
        var now = JAN.plus(Duration.ofDays(1));

        var action = SeasonTimeline.nextAction(now, PAIR, SETTINGS, List.of(), allAnnouncedFor(A), Optional.empty());

        assertThat(action)
                .as("the first season's world is due at once when the server starts after its preparation instant")
                .isEqualTo(new SeasonAction.Prepare(A, JAN.minus(Duration.ofMinutes(15))));
        assertThat(SeasonTimeline.isDue(action, now))
                .as("a preparation whose instant has passed is due immediately")
                .isTrue();
    }

    @Test
    void switchesAtOnceWhenThePreparedFirstSeasonIsAlreadyRunning() {
        var now = JAN.plus(Duration.ofDays(1));

        var action = SeasonTimeline.nextAction(now, PAIR, SETTINGS, List.of(run("s01", PREPARED)),
                allAnnouncedFor(A), Optional.empty());

        assertThat(action)
                .as("a prepared first season that started before the server came up switches at once")
                .isEqualTo(new SeasonAction.Switch(SeasonId.LEGACY, A.id(), JAN));
        assertThat(SeasonTimeline.isDue(action, now))
                .as("the switch instant has passed, so the switch is due")
                .isTrue();
    }

    @Test
    void warnsAtTheEndInstantWhenNoNextSeasonIsConfigured() {
        var action = SeasonTimeline.nextAction(BOUNDARY.minus(Duration.ofDays(1)), new SeasonCatalog(List.of(A)),
                SETTINGS, List.of(run("s01", ACTIVE)), Set.of(), Optional.empty());

        assertThat(action)
                .as("the first warning is due at the end of the active season")
                .isEqualTo(new SeasonAction.WarnNoNext(BOUNDARY));
    }

    @Test
    void warnsAgainOneDayAfterTheLastWarning() {
        var action = SeasonTimeline.nextAction(BOUNDARY.plus(Duration.ofHours(2)), new SeasonCatalog(List.of(A)),
                SETTINGS, List.of(run("s01", ACTIVE)), Set.of(), Optional.of(BOUNDARY));

        assertThat(action)
                .as("admins are warned once per day while no next season exists")
                .isEqualTo(new SeasonAction.WarnNoNext(BOUNDARY.plus(Duration.ofDays(1))));
    }

    @Test
    void anActionIsDueOnlyOnceItsInstantHasPassed() {
        var prepare = new SeasonAction.Prepare(B, BOUNDARY.minus(Duration.ofMinutes(15)));

        assertThat(SeasonTimeline.isDue(prepare, BOUNDARY.minus(Duration.ofMinutes(16))))
                .as("a preparation one minute before its instant is not due yet")
                .isFalse();
        assertThat(SeasonTimeline.isDue(prepare, BOUNDARY.minus(Duration.ofMinutes(15))))
                .as("a preparation is due at its instant")
                .isTrue();
    }
}

