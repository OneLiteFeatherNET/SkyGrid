package net.onelitefeather.skygridng.adapters.challengeprogress;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

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
}
