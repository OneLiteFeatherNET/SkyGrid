package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static net.onelitefeather.skygridng.api.model.SeasonPhase.ACTIVE;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.CLOSING;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARED;
import static net.onelitefeather.skygridng.api.model.SeasonPhase.PREPARE_FAILED;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.Announcer;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.Challenges;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.JAN;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.Journal;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.Resets;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.Runs;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.Worlds;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.run;
import static net.onelitefeather.skygridng.core.service.SeasonFakes.season;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the step methods of {@link SeasonTransition}. Every collaborator is an in memory double, the
 * instants are fixed and the seed supplier is constant, so no clock, world or database is touched.
 */
class SeasonTransitionTest {

    private static final Instant BOUNDARY = JAN.plus(SeasonFakes.FOUR_WEEKS);
    private static final Instant BEFORE_PREPARE = BOUNDARY.minus(Duration.ofMinutes(15));
    private static final String INCOMING_KEY = "skygridng:season_s02";
    private static final SeasonCatalog CATALOG = new SeasonCatalog(List.of(
            season("s01", JAN, "standard", "one_life"),
            season("s02", BOUNDARY, "classic", "no_crafting_table")));

    private Journal journal;
    private Runs runs;
    private Worlds worlds;
    private Challenges challenges;
    private Announcer announcer;

    @BeforeEach
    void setUp() {
        journal = new Journal();
        worlds = new Worlds(journal);
        challenges = new Challenges(journal, "one_life");
        challenges.known("no_crafting_table");
        announcer = new Announcer(journal);
    }

    private SeasonTransition transition() {
        return new SeasonTransition(CATALOG, runs, worlds, challenges, announcer, new Resets(journal),
                (from, to) -> journal.record("switched:" + from.value() + "->" + to.value()),
                () -> 7L,
                id -> {
                });
    }

    @Test
    void worldCreationFailureKeepsCurrentSeasonActive() {
        runs = new Runs(journal, run("s01", ACTIVE));
        worlds.failPrepare.add(INCOMING_KEY);

        transition().perform(new SeasonAction.Prepare(CATALOG.seasons().get(1), BEFORE_PREPARE), BEFORE_PREPARE);

        assertThat(runs.get("s02").phase())
                .as("a failed world creation marks the incoming season as PREPARE_FAILED")
                .isEqualTo(PREPARE_FAILED);
        assertThat(runs.get("s01").phase())
                .as("the current season must stay ACTIVE when the next world cannot be created")
                .isEqualTo(ACTIVE);
        assertThat(journal.count("deactivate:") + journal.count("switch:"))
                .as("no challenge change and no switch may happen before the world exists")
                .isZero();
        assertThat(announcer.alerts)
                .as("the failed creation is reported to the admins")
                .isNotEmpty();
    }

    @Test
    void resumeFromPreparedAfterBoundaryClosesOldSeason() {
        runs = new Runs(journal, run("s01", ACTIVE), run("s02", PREPARED));

        transition().perform(new SeasonAction.Resume(runs.get("s02")), BOUNDARY);

        assertThat(runs.get("s01").phase())
                .as("after the boundary a prepared next season closes the old one")
                .isEqualTo(CLOSING);
        assertThat(runs.get("s02").phase())
                .as("the prepared next season becomes the active one")
                .isEqualTo(ACTIVE);
        assertThat(journal.count("switch:"))
                .as("the switch is committed exactly once")
                .isEqualTo(1);
    }

    @Test
    void challengeDeactivationIsCreditedToEndingSeason() {
        runs = new Runs(journal, run("s01", ACTIVE), run("s02", PREPARED));

        transition().perform(new SeasonAction.Resume(runs.get("s02")), BOUNDARY);

        int deactivated = journal.entries().indexOf("deactivate:one_life");
        int switched = journal.entries().indexOf("switch:s01->s02");
        assertThat(deactivated)
                .as("the ending season's challenges are deactivated")
                .isGreaterThanOrEqualTo(0);
        assertThat(deactivated)
                .as("deactivation happens before the switch, while the current season is still the ending one")
                .isLessThan(switched);
    }

    @Test
    void persistsPhaseBeforeEachSideEffect() {
        runs = new Runs(journal, run("s01", ACTIVE));

        transition().perform(new SeasonAction.Prepare(CATALOG.seasons().get(1), BEFORE_PREPARE), BEFORE_PREPARE);

        int saving = journal.entries().indexOf("save:s02:PREPARING");
        int creating = journal.entries().indexOf("prepare:" + INCOMING_KEY + ":7:classic");
        int prepared = journal.entries().indexOf("save:s02:PREPARED");
        assertThat(saving)
                .as("the PREPARING phase and the seed are persisted before the world is created")
                .isGreaterThanOrEqualTo(0);
        assertThat(saving).as("PREPARING is saved before the world call").isLessThan(creating);
        assertThat(creating).as("the world is created with the stored key, seed and preset").isGreaterThanOrEqualTo(0);
        assertThat(creating).as("PREPARED is saved only after the world call").isLessThan(prepared);
    }
}
