package net.onelitefeather.skygridng.adapters.season;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Persistence;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.adapters.persistence.FreshTestDatabase;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonPhase;
import net.onelitefeather.skygridng.api.model.SeasonRun;
import net.onelitefeather.skygridng.ports.out.SeasonRunConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Round-trips {@link PostgresSeasonRunRepository} against a real PostgreSQL instance started by
 * Testcontainers, including the Flyway migration of the {@code season} unit. Each test runs against its own
 * fresh database and a fixed {@link Clock}; no sleeps and no system clock.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresSeasonRunRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private static final Instant SWITCH_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant STARTED = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant POSTPONED_END = Instant.parse("2026-12-01T20:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseConfig database;
    private PostgresSeasonRunRepository repository;

    @BeforeEach
    void startRepository() {
        database = FreshTestDatabase.create(POSTGRES);
        repository = new PostgresSeasonRunRepository(() -> database, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void stopRepository() {
        repository.close();
    }

    private static SeasonRun run(String id, SeasonPhase phase) {
        return new SeasonRun(new SeasonId(id), "skygridng:season_" + id, 42L, phase, 0, null, null);
    }

    private static SeasonRun activeRun(String id, Instant startedAt) {
        return new SeasonRun(new SeasonId(id), "skygridng:season_" + id, 42L, SeasonPhase.ACTIVE, 0, startedAt, null);
    }

    private String jdbcUrl() {
        return "jdbc:postgresql://" + database.host() + ":" + database.port() + "/" + database.name();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), database.user(), database.password());
    }

    @Test
    void saveAndFindRoundTripsEveryField() {
        var run = new SeasonRun(new SeasonId("s01"), "skygridng:season_s01", 7L, SeasonPhase.ACTIVE, 2, STARTED, null);
        repository.save(run);

        assertThat(repository.find(new SeasonId("s01")))
                .as("a saved run must be found with the same values")
                .contains(run);
    }

    @Test
    void postponedEndAndSentAnnouncementsRoundTrip() {
        var sent = Set.of("s02|SEVEN_DAYS|2026-12-01T18:00:00Z", "s02|ONE_DAY|2026-12-01T18:00:00Z");
        var run = new SeasonRun(new SeasonId("s01"), "skygridng:season_s01", 7L, SeasonPhase.ACTIVE, 0, STARTED,
                null, POSTPONED_END, sent);
        repository.save(run);

        assertThat(repository.find(new SeasonId("s01")))
                .as("the postponed end and the sent announcement keys must survive a save and a find")
                .contains(run);
    }

    @Test
    void findReturnsEmptyForAnUnknownSeason() {
        assertThat(repository.find(new SeasonId("unknown"))).isEmpty();
    }

    @Test
    void activeReturnsTheSingleActiveRun() {
        repository.save(activeRun("s01", STARTED));
        repository.save(run("s02", SeasonPhase.PREPARED));

        assertThat(repository.active()).contains(activeRun("s01", STARTED));
    }

    @Test
    void activeIsEmptyWhenNoRunIsActive() {
        repository.save(run("s01", SeasonPhase.PREPARED));

        assertThat(repository.active()).isEmpty();
    }

    @Test
    void inFlightExcludesActiveAndArchivedRuns() {
        repository.save(activeRun("s01", STARTED));
        repository.save(run("s02", SeasonPhase.PREPARING));
        repository.save(run("s03", SeasonPhase.ARCHIVED));
        repository.save(run("s04", SeasonPhase.PREPARE_FAILED));

        assertThat(repository.inFlight())
                .extracting(run -> run.seasonId().value())
                .containsExactlyInAnyOrder("s02", "s04");
    }

    /**
     * Two saves of one run from stale copies: two {@code EntityManager}s load the same run (version 0), the first
     * flush commits (version 1), and the second flush throws {@link OptimisticLockException}. Sequential, no threads.
     * The test drives the entity layer directly through a test factory on the same persistence unit, because a
     * {@link SeasonRun} carries no revision.
     */
    @Test
    void staleCopySaveFailsWithOptimisticLockException() {
        repository.save(run("s01", SeasonPhase.PREPARED));
        var factory = Persistence.createEntityManagerFactory("season", Map.of(
                "jakarta.persistence.jdbc.url", jdbcUrl(),
                "jakarta.persistence.jdbc.user", database.user(),
                "jakarta.persistence.jdbc.password", database.password()));
        try {
            var first = factory.createEntityManager();
            var second = factory.createEntityManager();
            first.getTransaction().begin();
            second.getTransaction().begin();
            var firstCopy = first.find(SeasonRunEntity.class, "s01");
            var secondCopy = second.find(SeasonRunEntity.class, "s01");

            firstCopy.setPhase(SeasonPhase.PREPARING);
            first.getTransaction().commit();
            first.close();

            secondCopy.setAttempts(1);
            assertThatExceptionOfType(OptimisticLockException.class)
                    .as("the second flush of a stale copy must be rejected by @Version")
                    .isThrownBy(second::flush);
            second.getTransaction().rollback();
            second.close();

            var check = factory.createEntityManager();
            var stored = check.find(SeasonRunEntity.class, "s01");
            assertThat(stored.getVersion())
                    .as("the stored version must be 1 after the first save, unchanged by the rejected second save")
                    .isEqualTo(1L);
            check.close();
        } finally {
            factory.close();
        }
    }

    @Test
    void switchActiveClosesTheOldRunAndActivatesTheNewRun() {
        repository.save(activeRun("s01", STARTED));
        repository.save(run("s02", SeasonPhase.PREPARED));

        repository.switchActive(new SeasonId("s01"), new SeasonId("s02"), SWITCH_AT);

        assertThat(repository.find(new SeasonId("s01")))
                .as("the old run must be closing and end at the switch instant")
                .contains(new SeasonRun(new SeasonId("s01"), "skygridng:season_s01", 42L,
                        SeasonPhase.CLOSING, 0, STARTED, SWITCH_AT));
        assertThat(repository.find(new SeasonId("s02")))
                .as("the new run must be active and start at the switch instant")
                .contains(new SeasonRun(new SeasonId("s02"), "skygridng:season_s02", 42L,
                        SeasonPhase.ACTIVE, 0, SWITCH_AT, null));
    }

    @Test
    void switchActiveFromLegacyActivatesTheIncomingRunWithoutAStoredLegacyRow() {
        repository.save(run("s01", SeasonPhase.PREPARED));

        repository.switchActive(SeasonId.LEGACY, new SeasonId("s01"), SWITCH_AT);

        assertThat(repository.find(new SeasonId("s01")))
                .as("the first season becomes ACTIVE and starts at the switch instant")
                .contains(new SeasonRun(new SeasonId("s01"), "skygridng:season_s01", 42L,
                        SeasonPhase.ACTIVE, 0, SWITCH_AT, null));
        assertThat(repository.find(SeasonId.LEGACY))
                .as("the implicit legacy season never gets a stored row")
                .isEmpty();
    }

    /**
     * The old run is flushed to CLOSING before the new run becomes ACTIVE. If the new run's ACTIVE write fails
     * (here another ACTIVE run still exists), both runs must stay unchanged, so no moment with two active seasons
     * is ever committed.
     */
    @Test
    void failedSwitchRollsBackBothRuns() {
        repository.save(activeRun("s01", STARTED));
        repository.save(run("s02", SeasonPhase.PREPARED));
        repository.save(run("s03", SeasonPhase.PREPARED));

        assertThatExceptionOfType(SeasonRunConflictException.class)
                .isThrownBy(() -> repository.switchActive(new SeasonId("s02"), new SeasonId("s03"), SWITCH_AT));

        assertThat(repository.find(new SeasonId("s02")))
                .as("the outgoing run must be rolled back to its previous phase")
                .contains(run("s02", SeasonPhase.PREPARED));
        assertThat(repository.find(new SeasonId("s03")))
                .as("the incoming run must stay unchanged")
                .contains(run("s03", SeasonPhase.PREPARED));
        assertThat(repository.active()).contains(activeRun("s01", STARTED));
    }

    @Test
    void partialUniqueIndexRejectsASecondActiveRow() throws SQLException {
        repository.save(activeRun("s01", STARTED));
        repository.save(run("s02", SeasonPhase.PREPARED));

        assertThatExceptionOfType(SQLException.class)
                .as("the database itself must refuse a second ACTIVE row")
                .isThrownBy(() -> {
                    try (Connection connection = connect(); var statement = connection.createStatement()) {
                        statement.executeUpdate("UPDATE season_run SET phase = 'ACTIVE' WHERE season_id = 's02'");
                    }
                })
                .withMessageContaining("season_run_single_active");
    }

    @Test
    void switchActiveReportsASecondActiveRowAsConflict() {
        repository.save(activeRun("s01", STARTED));
        repository.save(run("s02", SeasonPhase.PREPARED));
        repository.save(run("s03", SeasonPhase.PREPARED));

        assertThatExceptionOfType(SeasonRunConflictException.class)
                .as("a switch that would create a second ACTIVE row is a failed switch")
                .isThrownBy(() -> repository.switchActive(new SeasonId("s02"), new SeasonId("s03"), SWITCH_AT));
        assertThat(repository.inFlight())
                .extracting(run -> run.seasonId().value())
                .containsExactlyInAnyOrder("s02", "s03");
        assertThat(repository.active()).contains(activeRun("s01", STARTED));
    }
}
