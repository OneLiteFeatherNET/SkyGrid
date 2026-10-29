package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.adapters.persistence.Database;
import net.onelitefeather.skygridng.adapters.persistence.FreshTestDatabase;
import net.onelitefeather.skygridng.api.model.SeasonId;
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
 * Round-trips {@link PostgresChallengeProgressRepository} against a real, disposable PostgreSQL
 * instance started by Testcontainers, including the actual Flyway migrations. Each test runs against
 * its own fresh database.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresChallengeProgressRepositoryTest {

    private static final SeasonId SEASON_ONE = new SeasonId("s01");
    private static final SeasonId SEASON_TWO = new SeasonId("s02");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Database database;
    private PostgresChallengeProgressRepository repository;

    @BeforeEach
    void startRepository() {
        var config = FreshTestDatabase.create(POSTGRES);
        database = new Database(() -> config);
        repository = new PostgresChallengeProgressRepository(database);
    }

    @AfterEach
    void stopRepository() {
        database.close();
    }

    @Test
    void tierOfReturnsZeroBeforeAnyCompletion() {
        var playerId = UUID.randomUUID();

        assertThat(repository.tierOf(SEASON_ONE, playerId, "one_life")).isZero();
    }

    @Test
    void recordCompletionCreatesAtTierOneThenIncrements() {
        var playerId = UUID.randomUUID();

        var firstTier = repository.recordCompletion(SEASON_ONE, playerId, "one_life");
        var secondTier = repository.recordCompletion(SEASON_ONE, playerId, "one_life");

        assertThat(firstTier).isEqualTo(1);
        assertThat(secondTier).isEqualTo(2);
        assertThat(repository.tierOf(SEASON_ONE, playerId, "one_life")).isEqualTo(2);
    }

    @Test
    void differentPlayerChallengePairsAreIndependent() {
        var playerA = UUID.randomUUID();
        var playerB = UUID.randomUUID();

        repository.recordCompletion(SEASON_ONE, playerA, "one_life");
        repository.recordCompletion(SEASON_ONE, playerA, "one_life");
        repository.recordCompletion(SEASON_ONE, playerB, "no_crafting_table");

        assertThat(repository.tierOf(SEASON_ONE, playerA, "one_life")).isEqualTo(2);
        assertThat(repository.tierOf(SEASON_ONE, playerB, "no_crafting_table")).isEqualTo(1);
        assertThat(repository.tierOf(SEASON_ONE, playerB, "one_life")).isZero();
        assertThat(repository.tierOf(SEASON_ONE, playerA, "no_crafting_table")).isZero();
    }

    @Test
    void sameChallengeInTwoSeasonsKeepsSeparateTiers() {
        var playerId = UUID.randomUUID();

        repository.recordCompletion(SEASON_ONE, playerId, "one_life");
        repository.recordCompletion(SEASON_ONE, playerId, "one_life");
        repository.recordCompletion(SEASON_TWO, playerId, "one_life");

        assertThat(repository.tierOf(SEASON_ONE, playerId, "one_life"))
                .as("tier in season one must not be affected by season two")
                .isEqualTo(2);
        assertThat(repository.tierOf(SEASON_TWO, playerId, "one_life"))
                .as("tier in season two starts at its own count")
                .isEqualTo(1);
    }

    @Test
    void resetSeasonClearsOnlyThatSeason() {
        var playerId = UUID.randomUUID();
        repository.recordCompletion(SEASON_ONE, playerId, "one_life");
        repository.recordCompletion(SEASON_TWO, playerId, "one_life");

        repository.resetSeason(SEASON_ONE);

        assertThat(repository.tierOf(SEASON_ONE, playerId, "one_life"))
                .as("the reset season must be cleared")
                .isZero();
        assertThat(repository.tierOf(SEASON_TWO, playerId, "one_life"))
                .as("other seasons must be left unchanged")
                .isEqualTo(1);
    }

    @Test
    void historyReturnsOneTierPerSeason() {
        var playerId = UUID.randomUUID();
        repository.recordCompletion(SEASON_ONE, playerId, "one_life");
        repository.recordCompletion(SEASON_ONE, playerId, "one_life");
        repository.recordCompletion(SEASON_ONE, playerId, "one_life");
        repository.recordCompletion(SEASON_TWO, playerId, "one_life");

        assertThat(repository.history(playerId))
                .isEqualTo(Map.of(SEASON_ONE, 3, SEASON_TWO, 1));
    }

    /**
     * Proves that the pessimistic-write lock in {@link PostgresChallengeProgressRepository#recordCompletion}
     * actually serializes two genuinely concurrent completions for the same player and challenge,
     * instead of racing: both calls must apply, and no update may be lost. Kept as a threaded test
     * from before the season change; it is the only test in this class that uses threads.
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
                return repository.recordCompletion(SEASON_ONE, playerId, challengeId);
            };

            var firstCall = executor.submit(completeOnceBothThreadsAreReady);
            var secondCall = executor.submit(completeOnceBothThreadsAreReady);

            var firstTier = firstCall.get(10, TimeUnit.SECONDS);
            var secondTier = secondCall.get(10, TimeUnit.SECONDS);

            assertThat(Set.of(firstTier, secondTier)).isEqualTo(Set.of(1, 2));
            assertThat(repository.tierOf(SEASON_ONE, playerId, challengeId)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }
}
