package net.onelitefeather.skygridng.adapters.taskprogress;

import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips {@link PostgresTaskCompletionRepository} against a real, disposable PostgreSQL
 * instance started by Testcontainers, including the actual Flyway migration; nothing here is
 * mocked at the JDBC level.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresTaskCompletionRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static PostgresTaskCompletionRepository repository;

    @BeforeAll
    static void startRepository() {
        var config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        repository = new PostgresTaskCompletionRepository(() -> config);
    }

    @AfterAll
    static void stopRepository() {
        if (repository != null) {
            repository.close();
        }
    }

    @Test
    void isCompletedReturnsFalseBeforeAnyMarkCompletedCall() {
        var playerId = UUID.randomUUID();

        assertThat(repository.isCompleted(playerId, "one_life", "survive_a_day")).isFalse();
    }

    @Test
    void markCompletedReturnsTrueOnceThenFalseAndIsIdempotent() {
        var playerId = UUID.randomUUID();

        var first = repository.markCompleted(playerId, "one_life", "survive_a_day");
        var second = repository.markCompleted(playerId, "one_life", "survive_a_day");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(repository.isCompleted(playerId, "one_life", "survive_a_day")).isTrue();
    }

    @Test
    void differentTasksForSamePlayerAreIndependentRows() {
        var playerId = UUID.randomUUID();

        repository.markCompleted(playerId, "one_life", "survive_a_day");

        assertThat(repository.isCompleted(playerId, "one_life", "survive_a_day")).isTrue();
        assertThat(repository.isCompleted(playerId, "one_life", "no_death")).isFalse();
    }

    @Test
    void differentChallengesForSamePlayerAreIndependentRows() {
        var playerId = UUID.randomUUID();

        repository.markCompleted(playerId, "one_life", "survive_a_day");

        assertThat(repository.isCompleted(playerId, "one_life", "survive_a_day")).isTrue();
        assertThat(repository.isCompleted(playerId, "no_crafting_table", "survive_a_day")).isFalse();
    }

    @Test
    void resetAllClearsEveryRow() {
        var playerId = UUID.randomUUID();
        repository.markCompleted(playerId, "one_life", "survive_a_day");

        repository.resetAll();

        assertThat(repository.isCompleted(playerId, "one_life", "survive_a_day")).isFalse();
    }

    /**
     * Proves that two genuinely concurrent {@code markCompleted} calls for the exact same, brand-new
     * (player, challenge, task) triple never both succeed and never throw unhandled: exactly one call
     * must return {@code true} (the winner of the primary key race), the other {@code false} (the
     * retried existence check after the loser's insert fails on the constraint). A
     * {@link CyclicBarrier} with two parties forces both worker threads to reach
     * {@code markCompleted} at the same instant rather than running sequentially by accident. Each
     * future is bounded with a timeout so a regression that deadlocks or hangs fails this test loudly
     * instead of hanging CI forever.
     */
    @Test
    void concurrentMarkCompletedForSameBrandNewTripleExactlyOneWins() throws Exception {
        var playerId = UUID.randomUUID();
        var challengeId = "one_life";
        var taskId = "survive_a_day";
        var startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> markCompletedOnceBothThreadsAreReady = () -> {
                startBarrier.await();
                return repository.markCompleted(playerId, challengeId, taskId);
            };

            var firstCall = executor.submit(markCompletedOnceBothThreadsAreReady);
            var secondCall = executor.submit(markCompletedOnceBothThreadsAreReady);

            var firstResult = firstCall.get(10, TimeUnit.SECONDS);
            var secondResult = secondCall.get(10, TimeUnit.SECONDS);

            assertThat(Set.of(firstResult, secondResult)).isEqualTo(Set.of(true, false));
            assertThat(repository.isCompleted(playerId, challengeId, taskId)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }
}
