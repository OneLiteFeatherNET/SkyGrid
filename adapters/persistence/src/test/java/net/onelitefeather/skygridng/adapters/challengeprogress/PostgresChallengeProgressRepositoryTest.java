package net.onelitefeather.skygridng.adapters.challengeprogress;

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
 * Round-trips {@link PostgresChallengeProgressRepository} against a real, disposable PostgreSQL
 * instance started by Testcontainers, including the actual Flyway migration; nothing here is
 * mocked at the JDBC level.
 */
@Testcontainers
class PostgresChallengeProgressRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static PostgresChallengeProgressRepository repository;

    @BeforeAll
    static void startRepository() {
        var config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        repository = new PostgresChallengeProgressRepository(() -> config);
    }

    @AfterAll
    static void stopRepository() {
        if (repository != null) {
            repository.close();
        }
    }

    @Test
    void tierOfReturnsZeroBeforeAnyCompletion() {
        var playerId = UUID.randomUUID();

        assertThat(repository.tierOf(playerId, "one_life")).isZero();
    }

    @Test
    void recordCompletionCreatesAtTierOneThenIncrements() {
        var playerId = UUID.randomUUID();

        var firstTier = repository.recordCompletion(playerId, "one_life");
        var secondTier = repository.recordCompletion(playerId, "one_life");

        assertThat(firstTier).isEqualTo(1);
        assertThat(secondTier).isEqualTo(2);
        assertThat(repository.tierOf(playerId, "one_life")).isEqualTo(2);
    }

    @Test
    void differentPlayerChallengePairsAreIndependent() {
        var playerA = UUID.randomUUID();
        var playerB = UUID.randomUUID();

        repository.recordCompletion(playerA, "one_life");
        repository.recordCompletion(playerA, "one_life");
        repository.recordCompletion(playerB, "no_crafting_table");

        assertThat(repository.tierOf(playerA, "one_life")).isEqualTo(2);
        assertThat(repository.tierOf(playerB, "no_crafting_table")).isEqualTo(1);
        assertThat(repository.tierOf(playerB, "one_life")).isZero();
        assertThat(repository.tierOf(playerA, "no_crafting_table")).isZero();
    }

    @Test
    void resetAllClearsEveryPlayerAndChallenge() {
        var playerId = UUID.randomUUID();
        repository.recordCompletion(playerId, "one_life");

        repository.resetAll();

        assertThat(repository.tierOf(playerId, "one_life")).isZero();
    }

    /**
     * Proves that the pessimistic-write lock in {@link PostgresChallengeProgressRepository#recordCompletion}
     * actually serializes two genuinely concurrent completions for the same player and challenge,
     * instead of racing: both calls must apply, and no update may be lost. A {@link CyclicBarrier}
     * with two parties forces both worker threads to reach {@code recordCompletion} at the same
     * instant rather than running sequentially by accident, so whichever thread loses the race for
     * the row lock genuinely has to wait on it, then observe the other thread's write. Each future is
     * bounded with a timeout so a regression that deadlocks under the lock (for example a lock
     * upgrade issue) fails this test loudly instead of hanging CI forever.
     */
    @Test
    void concurrentRecordCompletionsForSamePlayerChallengeBothApplyWithoutLostUpdate() throws Exception {
        var playerId = UUID.randomUUID();
        var challengeId = "one_life";
        var startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> completeOnceBothThreadsAreReady = () -> {
                startBarrier.await();
                return repository.recordCompletion(playerId, challengeId);
            };

            var firstCall = executor.submit(completeOnceBothThreadsAreReady);
            var secondCall = executor.submit(completeOnceBothThreadsAreReady);

            var firstTier = firstCall.get(10, TimeUnit.SECONDS);
            var secondTier = secondCall.get(10, TimeUnit.SECONDS);

            assertThat(Set.of(firstTier, secondTier)).isEqualTo(Set.of(1, 2));
            assertThat(repository.tierOf(playerId, challengeId)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }
}
