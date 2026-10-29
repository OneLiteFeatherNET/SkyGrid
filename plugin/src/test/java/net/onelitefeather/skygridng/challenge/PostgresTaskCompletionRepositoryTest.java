package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.persistence.Database;
import net.onelitefeather.skygridng.persistence.FreshTestDatabase;
import net.onelitefeather.skygridng.season.SeasonId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips {@link PostgresTaskCompletionRepository} against a real, disposable PostgreSQL instance
 * started by Testcontainers, including the actual Flyway migrations. Each test runs against its own
 * fresh database.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresTaskCompletionRepositoryTest {

    private static final SeasonId SEASON_ONE = new SeasonId("s01");
    private static final SeasonId SEASON_TWO = new SeasonId("s02");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Database database;
    private PostgresTaskCompletionRepository repository;

    @BeforeEach
    void startRepository() {
        var config = FreshTestDatabase.create(POSTGRES);
        database = new Database(() -> config);
        repository = new PostgresTaskCompletionRepository(database);
    }

    @AfterEach
    void stopRepository() {
        database.close();
    }

    private static ProgressScope scope(SeasonId season, UUID playerId, String challengeId, int prestigeStep) {
        return new ProgressScope(season, playerId, challengeId, prestigeStep);
    }

    @Test
    void isCompletedReturnsFalseBeforeAnyMarkCompletedCall() {
        var scope = scope(SEASON_ONE, UUID.randomUUID(), "one_life", 0);

        assertThat(repository.isCompleted(scope, "survive_a_day")).isFalse();
    }

    @Test
    void markCompletedReturnsTrueOnceThenFalseAndIsIdempotent() {
        var scope = scope(SEASON_ONE, UUID.randomUUID(), "one_life", 0);

        assertThat(repository.markCompleted(scope, "survive_a_day")).isTrue();
        assertThat(repository.markCompleted(scope, "survive_a_day")).isFalse();
        assertThat(repository.isCompleted(scope, "survive_a_day")).isTrue();
    }

    @Test
    void differentTasksForSamePlayerAreIndependentRows() {
        var playerId = UUID.randomUUID();
        repository.markCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_day");

        assertThat(repository.isCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_week")).isFalse();
    }

    @Test
    void sameTaskInTwoSeasonsIsIndependent() {
        var playerId = UUID.randomUUID();
        repository.markCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_day");

        assertThat(repository.isCompleted(scope(SEASON_TWO, playerId, "one_life", 0), "survive_a_day"))
                .as("a completion in season one must not count in season two")
                .isFalse();
    }

    @Test
    void sameTaskAtDifferentPrestigeStepsIsIndependent() {
        var playerId = UUID.randomUUID();
        repository.markCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_day");

        assertThat(repository.isCompleted(scope(SEASON_ONE, playerId, "one_life", 1), "survive_a_day"))
                .as("the prestige step is part of the key")
                .isFalse();
    }

    @Test
    void resetSeasonLeavesOtherSeasonsUnchanged() {
        var playerId = UUID.randomUUID();
        repository.markCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_day");
        repository.markCompleted(scope(SEASON_TWO, playerId, "one_life", 0), "survive_a_day");

        repository.resetSeason(SEASON_ONE);

        assertThat(repository.isCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_day"))
                .as("the reset season must be cleared")
                .isFalse();
        assertThat(repository.isCompleted(scope(SEASON_TWO, playerId, "one_life", 0), "survive_a_day"))
                .as("other seasons must be left unchanged")
                .isTrue();
    }

    @Test
    void completedCountBySeasonReturnsCountsPerSeason() {
        var playerId = UUID.randomUUID();
        repository.markCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_day");
        repository.markCompleted(scope(SEASON_ONE, playerId, "one_life", 0), "survive_a_week");
        repository.markCompleted(scope(SEASON_TWO, playerId, "one_life", 0), "survive_a_day");

        assertThat(repository.completedCountBySeason(playerId))
                .isEqualTo(Map.of(SEASON_ONE, 2L, SEASON_TWO, 1L));
    }

    /**
     * Two genuinely concurrent first completions of the same brand new triple: exactly one must
     * report it inserted, and the row must exist afterwards. Kept as a threaded test from before the
     * season change; it is the only test in this class that uses threads.
     */
    @Test
    void concurrentMarkCompletedForSameBrandNewTripleExactlyOneWins() throws Exception {
        var scope = scope(SEASON_ONE, UUID.randomUUID(), "one_life", 0);
        var taskId = "survive_a_day";
        var startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> markCompletedOnceBothThreadsAreReady = () -> {
                startBarrier.await();
                return repository.markCompleted(scope, taskId);
            };

            var firstCall = executor.submit(markCompletedOnceBothThreadsAreReady);
            var secondCall = executor.submit(markCompletedOnceBothThreadsAreReady);

            var firstResult = firstCall.get(10, TimeUnit.SECONDS);
            var secondResult = secondCall.get(10, TimeUnit.SECONDS);

            assertThat(Set.of(firstResult, secondResult)).isEqualTo(Set.of(true, false));
            assertThat(repository.isCompleted(scope, taskId)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
