package net.onelitefeather.skygridng.season;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.onelitefeather.skygridng.season.SeasonPhase.ACTIVE;
import static net.onelitefeather.skygridng.season.SeasonPhase.ARCHIVED;
import static net.onelitefeather.skygridng.season.SeasonPhase.ARCHIVING;
import static net.onelitefeather.skygridng.season.SeasonPhase.ARCHIVE_FAILED;
import static net.onelitefeather.skygridng.season.SeasonPhase.CLOSING;
import static net.onelitefeather.skygridng.season.SeasonPhase.PREPARED;
import static net.onelitefeather.skygridng.season.SeasonPhase.PREPARING;
import static net.onelitefeather.skygridng.season.SeasonFakes.Announcer;
import static net.onelitefeather.skygridng.season.SeasonFakes.CapturingHandler;
import static net.onelitefeather.skygridng.season.SeasonFakes.Challenges;
import static net.onelitefeather.skygridng.season.SeasonFakes.FOUR_WEEKS;
import static net.onelitefeather.skygridng.season.SeasonFakes.JAN;
import static net.onelitefeather.skygridng.season.SeasonFakes.Journal;
import static net.onelitefeather.skygridng.season.SeasonFakes.Resets;
import static net.onelitefeather.skygridng.season.SeasonFakes.Runs;
import static net.onelitefeather.skygridng.season.SeasonFakes.Worlds;
import static net.onelitefeather.skygridng.season.SeasonFakes.run;
import static net.onelitefeather.skygridng.season.SeasonFakes.season;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SeasonService}: the single-flight guard, the refusal without a database, the postponement
 * and the isolation of a failing transition listener. Every collaborator is an in memory double and every instant
 * is fixed, so nothing here sleeps, reads the clock or touches a server.
 */
class SeasonServiceTest {

    private static final Instant BOUNDARY = JAN.plus(FOUR_WEEKS);
    private static final Instant BEFORE_PREPARE = BOUNDARY.minus(Duration.ofMinutes(15));
    private static final String ENDING_KEY = "skygridng:season_s01";
    private static final String INCOMING_KEY = "skygridng:season_s02";
    private static final SeasonCatalog CATALOG = new SeasonCatalog(List.of(
            season("s01", JAN, "standard", "one_life"),
            season("s02", BOUNDARY, "classic", "no_crafting_table")));
    private static final int MAX_STEPS = 20;

    private static final Logger LOGGER = Logger.getLogger(SeasonService.class.getName());

    private Journal journal;
    private Worlds worlds;
    private Challenges challenges;
    private Announcer announcer;
    private Runs runs;
    private CapturingHandler log;

    @BeforeEach
    void setUp() {
        journal = new Journal();
        worlds = new Worlds(journal);
        challenges = new Challenges(journal, "one_life");
        challenges.known("no_crafting_table");
        announcer = new Announcer(journal);
        log = new CapturingHandler();
        LOGGER.addHandler(log);
    }

    @AfterEach
    void tearDown() {
        LOGGER.removeHandler(log);
    }

    private SeasonService service(Set<SeasonTransitionListener> listeners, SeasonRun... initial) {
        runs = new Runs(journal, initial);
        return new SeasonService(CATALOG, SeasonSettings.defaults(), runs, worlds, challenges, announcer,
                new Resets(journal), listeners, () -> 99L, () -> CATALOG);
    }

    /** A new service over the same fake database, as after a server restart. */
    private SeasonService restarted(Set<SeasonTransitionListener> listeners) {
        return new SeasonService(CATALOG, SeasonSettings.defaults(), runs, worlds, challenges, announcer,
                new Resets(journal), listeners, () -> 99L, () -> CATALOG);
    }

    /** Ticks at {@code now} until no step is due, the way the scheduler does over a few seconds. */
    private static void drain(SeasonService service, Instant now) {
        for (int step = 0; step < MAX_STEPS && service.tick(now); step++) {
            assertThat(step).as("the transition must settle within the step budget").isLessThan(MAX_STEPS - 1);
        }
    }

    @Test
    void statusNamesTheActiveSeasonItsPhaseAndTheNextSeason() {
        SeasonService service = service(Set.of(), run("s01", ACTIVE));
        service.tick(JAN.plus(Duration.ofDays(1)));

        assertThat(service.status(JAN.plus(Duration.ofDays(1))))
                .as("status reads the active season and its phase, and names the season that follows")
                .hasValueSatisfying(status -> {
                    assertThat(status.activeSeason().value()).isEqualTo("s01");
                    assertThat(status.phase()).isEqualTo(ACTIVE);
                    assertThat(status.nextSeason().value()).isEqualTo("s02");
                });
    }

    @Test
    void legacyWithAnUpcomingSeasonNamesThatSeasonAsNextAndItsStartAsNextAction() {
        SeasonService service = service(Set.of());

        assertThat(service.status(JAN.minus(Duration.ofDays(1))))
                .as("while the legacy season runs, the first season that has not ended is the next one, starting at its start")
                .hasValueSatisfying(status -> {
                    assertThat(status.activeSeason()).isEqualTo(SeasonId.LEGACY);
                    assertThat(status.nextSeason().value()).isEqualTo("s01");
                    assertThat(status.nextActionAt()).isEqualTo(JAN);
                });
    }

    @Test
    void legacyWithoutAnySeasonHasNoNextSeason() {
        runs = new Runs(journal);
        SeasonService service = new SeasonService(SeasonCatalog.empty(), SeasonSettings.defaults(), runs, worlds,
                challenges, announcer, new Resets(journal), Set.of(), () -> 99L, SeasonCatalog::empty);

        assertThat(service.status(JAN.minus(Duration.ofDays(1))))
                .as("with no configured season the status names no next season and no next action")
                .hasValueSatisfying(status -> {
                    assertThat(status.activeSeason()).isEqualTo(SeasonId.LEGACY);
                    assertThat(status.nextSeason()).isNull();
                    assertThat(status.nextActionAt()).isNull();
                });
    }

    @Test
    void reloadKeepsTheActiveSeasonWindowAndTakesTheFreshDefinitionsOfTheNextOne() {
        SeasonCatalog fresh = new SeasonCatalog(List.of(
                season("s01", JAN, "standard", "one_life"),
                season("s02", BOUNDARY, "desert", "one_life")));
        runs = new Runs(journal, run("s01", ACTIVE));
        SeasonService service = new SeasonService(CATALOG, SeasonSettings.defaults(), runs, worlds, challenges,
                announcer, new Resets(journal), Set.of(), () -> 99L, () -> fresh);
        service.tick(JAN.plus(Duration.ofDays(1)));

        assertThat(service.reload())
                .as("a reload while no transition runs succeeds")
                .isTrue();
        assertThat(service.list().seasons().get(1).preset())
                .as("the next season takes the freshly read definition")
                .isEqualTo("desert");
    }

    @Test
    void firstSeasonIsPreparedThenSwitchedFromLegacyAtItsStart() {
        SeasonService service = service(Set.of());

        drain(service, JAN.minus(Duration.ofMinutes(15)));
        assertThat(runs.get("s01").phase())
                .as("the first season's world is prepared one lead before its start, with no active run")
                .isEqualTo(PREPARED);

        drain(service, JAN);
        assertThat(runs.get("s01").phase())
                .as("the first season becomes ACTIVE at its start")
                .isEqualTo(ACTIVE);
        assertThat(journal.count("switch:legacy->s01"))
                .as("the switch from legacy happens exactly once")
                .isEqualTo(1);
        assertThat(journal.count("archive:") + journal.count("unload:"))
                .as("the legacy world is never unloaded or archived")
                .isZero();
    }

    @Test
    void firstSeasonNoticeIsRememberedWithoutAnActiveRun() {
        SeasonService service = service(Set.of());
        Instant sevenDaysBefore = JAN.minus(Duration.ofDays(7));

        assertThat(service.tick(sevenDaysBefore))
                .as("the seven day notice of the first season is due at its instant")
                .isTrue();
        assertThat(service.tick(sevenDaysBefore))
                .as("a notice that was sent is recorded, so the same tick sends nothing again")
                .isFalse();
        assertThat(journal.count("upcoming:s01:"))
                .as("the seven day notice is sent exactly once")
                .isEqualTo(1);
    }

    @Test
    void secondTickDuringTransitionIsANoOp() {
        List<Boolean> nested = new ArrayList<>();
        SeasonService service = service(Set.of(), run("s01", ACTIVE));
        worlds.beforePrepare = () -> nested.add(service.tick(BEFORE_PREPARE));

        drain(service, BEFORE_PREPARE);

        assertThat(nested)
                .as("a tick that arrives while a transition step runs must do nothing")
                .containsExactly(false);
        assertThat(journal.count("prepare:"))
                .as("the next world is created exactly once")
                .isEqualTo(1);
    }

    @Test
    void advanceAndRetryNeverRunTwoTransitionsAtOnce() {
        List<Boolean> nested = new ArrayList<>();
        SeasonService service = service(Set.of(), run("s01", ACTIVE));
        worlds.beforePrepare = () -> {
            nested.add(service.advance("admin", BEFORE_PREPARE));
            nested.add(service.advanceNow("admin", BEFORE_PREPARE));
        };

        drain(service, BEFORE_PREPARE);

        assertThat(nested)
                .as("an admin advance that arrives during a running step is refused")
                .containsExactly(false, false);
        assertThat(journal.count("save:s02:PREPARING"))
                .as("the preparation of the next season is persisted once")
                .isEqualTo(1);
    }

    @Test
    void databaseDownRefusesEveryTransition() {
        SeasonService service = service(Set.of(), run("s01", ACTIVE));
        runs.down = true;

        assertThat(service.tick(BEFORE_PREPARE)).as("a tick is refused while the database is down").isFalse();
        assertThat(service.advance("admin", BEFORE_PREPARE)).as("advance is refused").isFalse();
        assertThat(service.advanceNow("admin", BEFORE_PREPARE)).as("advance now is refused").isFalse();
        assertThat(service.retry(BEFORE_PREPARE)).as("retry is refused").isFalse();
        assertThat(service.postpone(BEFORE_PREPARE, Duration.ofHours(2))).as("postpone is refused").isFalse();
        assertThat(journal.entries())
                .as("no world, challenge or run write may happen while the database is down")
                .isEmpty();
    }

    @Test
    void postponeShiftsOverlappingSeasons() {
        SeasonService service = service(Set.of(), run("s01", ACTIVE));

        boolean postponed = service.postpone(JAN.plus(Duration.ofDays(1)), Duration.ofHours(2));

        assertThat(postponed).as("a postponement of the active season is accepted").isTrue();
        assertThat(service.list().seasons().get(0).end())
                .as("the end of the active season moves by two hours")
                .isEqualTo(BOUNDARY.plus(Duration.ofHours(2)));
        assertThat(service.list().seasons().get(1).start())
                .as("the next season is shifted so it does not overlap the postponed one")
                .isEqualTo(BOUNDARY.plus(Duration.ofHours(2)));
    }

    @Test
    void failingListenerIsLoggedAndOthersStillRun() {
        worlds.loaded.add(ENDING_KEY);
        worlds.folders.add(ENDING_KEY);
        SeasonTransitionListener failing = (from, to) -> {
            throw new IllegalStateException("prestige listener broke");
        };
        SeasonTransitionListener other = (from, to) -> journal.record("listener:" + from.value() + "->" + to.value());
        SeasonService service = service(new LinkedHashSet<>(List.of(failing, other)),
                run("s01", ACTIVE), run("s02", PREPARED));

        drain(service, BOUNDARY);

        assertThat(journal.count("listener:"))
                .as("the remaining listeners still run after one listener fails")
                .isEqualTo(1);
        assertThat(runs.get("s02").phase())
                .as("the switch is already committed and is not rolled back")
                .isEqualTo(ACTIVE);
        assertThat(announcer.alerts)
                .as("the failing listener is reported to the admins")
                .anySatisfy(alert -> assertThat(alert).contains("s01", "s02"));
        assertThat(log.records())
                .as("the failing listener is logged with the season ids")
                .anySatisfy(record -> {
                    assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
                    assertThat(record.getMessage()).contains("s01", "s02");
                });
    }

    @Test
    void restartWithPreparingRunCreatesTheWorldWithTheStoredSeed() {
        SeasonService service = service(Set.of(), run("s01", ACTIVE), run("s02", PREPARING));

        drain(service, BEFORE_PREPARE);

        assertThat(journal.entries())
                .as("the world is created with the seed persisted before the restart, never a new one")
                .contains("prepare:" + INCOMING_KEY + ":7:classic");
    }

    @Test
    void restartWithPreparingRunLoadsAnExistingFolderAndMarksItPrepared() {
        worlds.folders.add(INCOMING_KEY);
        SeasonService service = service(Set.of(), run("s01", ACTIVE), run("s02", PREPARING));

        drain(service, BEFORE_PREPARE);

        assertThat(runs.get("s02").phase())
                .as("the existing folder is loaded with the stored seed and the run is PREPARED")
                .isEqualTo(PREPARED);
        assertThat(journal.entries())
                .as("the stored seed is used for the existing folder")
                .contains("prepare:" + INCOMING_KEY + ":7:classic");
    }

    @Test
    void restartWithPreparedRunBeforeTheBoundaryLoadsTheWorldAndWaits() {
        SeasonService service = service(Set.of(), run("s01", ACTIVE), run("s02", PREPARED));

        drain(service, BOUNDARY.minus(Duration.ofHours(1)));

        assertThat(worlds.isLoaded(INCOMING_KEY))
                .as("the prepared world is loaded again after a restart, with no players in it")
                .isTrue();
        assertThat(runs.get("s01").phase())
                .as("nothing switches before the boundary")
                .isEqualTo(ACTIVE);
    }

    @Test
    void restartAfterTheBoundarySwitchesOnceAndArchivesTheOldSeason() {
        worlds.loaded.add(ENDING_KEY);
        worlds.folders.add(ENDING_KEY);
        SeasonService service = service(Set.of(), run("s01", ACTIVE), run("s02", PREPARED));

        drain(service, BOUNDARY);

        assertThat(journal.count("switch:"))
                .as("the switch is committed exactly once, even after a restart")
                .isEqualTo(1);
        assertThat(runs.get("s02").phase()).as("the prepared season is active").isEqualTo(ACTIVE);
        assertThat(runs.get("s01").phase()).as("the old season ends archived").isEqualTo(ARCHIVED);
    }

    @Test
    void restartWhileTheOldSeasonClosesReappliesTheChallengesAndMovesThePlayers() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
        worlds.loaded.add(ENDING_KEY);
        worlds.folders.add(ENDING_KEY);
        worlds.loaded.add(INCOMING_KEY);
        worlds.folders.add(INCOMING_KEY);
        worlds.playersInWorld.add(player);
        SeasonService service = service(Set.of(), run("s01", CLOSING), run("s02", ACTIVE));

        drain(service, BOUNDARY);

        assertThat(journal.entries())
                .as("the incoming challenge set is applied again")
                .contains("activate:no_crafting_table");
        assertThat(journal.entries())
                .as("the players still in the ending world are moved to the incoming one")
                .contains("evacuate:" + ENDING_KEY + "->" + INCOMING_KEY);
        assertThat(journal.entries())
                .as("a moved player is reset for the incoming season")
                .contains("reset:" + player + ":s02");
        assertThat(runs.get("s01").phase())
                .as("the closing season finishes its steps")
                .isEqualTo(ARCHIVED);
    }

    @Test
    void restartWhileArchivingWithBothFoldersPresentMarksTheArchiveFailed() {
        worlds.folders.add(ENDING_KEY);
        worlds.archived.add(ENDING_KEY);
        SeasonService service = service(Set.of(), run("s01", ARCHIVING), run("s02", ACTIVE));

        drain(service, BOUNDARY);

        assertThat(runs.get("s01").phase())
                .as("a folder and an archived copy at once is a conflict, not an archive")
                .isEqualTo(ARCHIVE_FAILED);
        assertThat(announcer.alerts)
                .as("the conflict is reported to the admins")
                .isNotEmpty();
    }

    @Test
    void restartWhileArchivingWithOnlyTheArchivedCopyMarksTheSeasonArchived() {
        worlds.archived.add(ENDING_KEY);
        SeasonService service = service(Set.of(), run("s01", ARCHIVING), run("s02", ACTIVE));

        drain(service, BOUNDARY);

        assertThat(runs.get("s01").phase())
                .as("an archived copy without a folder means the move already happened")
                .isEqualTo(ARCHIVED);
    }

    @Test
    void advanceConfirmSchedulesNoEarlierThanMinNotice() {
        worlds.loaded.add(INCOMING_KEY);
        worlds.folders.add(INCOMING_KEY);
        Instant command = BOUNDARY.minus(Duration.ofDays(2));
        SeasonService service = service(Set.of(), run("s01", ACTIVE), run("s02", PREPARED));

        boolean accepted = service.advance("admin", command);
        drain(service, command.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(59)));

        assertThat(accepted).as("a confirmed advance is accepted for an active season with a next season").isTrue();
        assertThat(journal.count("switch:"))
                .as("the season does not switch before the minimum notice of 24 hours has passed")
                .isZero();
        assertThat(journal.entries())
                .as("the announcements for that boundary are sent")
                .anySatisfy(entry -> assertThat(entry).startsWith("upcoming:s02:"));

        drain(service, command.plus(Duration.ofHours(24)));

        assertThat(journal.count("switch:"))
                .as("the switch happens at the scheduled boundary, 24 hours after the command")
                .isEqualTo(1);
        assertThat(runs.get("s02").phase()).isEqualTo(ACTIVE);
    }

    @Test
    void advanceNowConfirmLogsActor() {
        Instant command = BOUNDARY.minus(Duration.ofDays(2));
        SeasonService service = service(Set.of(), run("s01", ACTIVE));

        boolean accepted = service.advanceNow("alice", command);

        assertThat(accepted).as("an immediate advance is accepted").isTrue();
        assertThat(log.records())
                .as("the log names the acting admin and the instant of the command")
                .anySatisfy(record -> {
                    assertThat(record.getLevel()).isEqualTo(Level.INFO);
                    assertThat(record.getMessage()).contains("alice", command.toString());
                });
    }

    @Test
    void postponedEndAndSentAnnouncementSurviveARestart() {
        SeasonService first = service(Set.of(), run("s01", ACTIVE));
        Instant postponedEnd = BOUNDARY.plus(Duration.ofHours(2));
        Instant sevenDaysBefore = postponedEnd.minus(Duration.ofDays(7));
        first.postpone(JAN.plus(Duration.ofDays(1)), Duration.ofHours(2));
        drain(first, sevenDaysBefore);

        SeasonService restarted = restarted(Set.of());
        drain(restarted, sevenDaysBefore);

        assertThat(restarted.list().seasons().get(0).end())
                .as("the postponed end of the active season is restored from the database after a restart")
                .isEqualTo(postponedEnd);
        assertThat(journal.count("upcoming:s02:"))
                .as("the seven day notice sent before the restart is not sent again")
                .isEqualTo(1);
    }
}
