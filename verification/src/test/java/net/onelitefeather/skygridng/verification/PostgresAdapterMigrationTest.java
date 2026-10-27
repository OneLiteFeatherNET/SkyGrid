package net.onelitefeather.skygridng.verification;

import net.onelitefeather.skygridng.adapters.challengeprogress.PostgresChallengeProgressRepository;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.adapters.taskprogress.PostgresTaskCompletionRepository;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reproduces the Flyway clash between the challenge progress and task progress adapters. Both
 * adapters are pointed at the same Postgres database, the way a single plugin install runs them. Each
 * test builds its own repositories and closes them again, so no state is shared between tests.
 */
@Testcontainers
class PostgresAdapterMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DatabaseConfig config() {
        return new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    /** Both adapters can run their migrations against one shared database without failing. */
    @Test
    void bothAdaptersMigrateTheSameDatabaseWithoutClashing() {
        var challengeProgress = new PostgresChallengeProgressRepository(PostgresAdapterMigrationTest::config);
        var taskProgress = new PostgresTaskCompletionRepository(PostgresAdapterMigrationTest::config);
        try {
            assertThatCode(() -> {
                        challengeProgress.tierOf(UUID.randomUUID(), "challenge");
                        taskProgress.isCompleted(UUID.randomUUID(), "challenge", "task");
                    })
                    .as("challenge progress and task progress migrations must both apply to one database")
                    .doesNotThrowAnyException();
        } finally {
            challengeProgress.close();
            taskProgress.close();
        }
    }

    /** Each adapter keeps its own Flyway history table, so both schemas are recorded separately. */
    @Test
    void eachAdapterRecordsItsMigrationsInItsOwnHistoryTable() throws SQLException {
        var challengeProgress = new PostgresChallengeProgressRepository(PostgresAdapterMigrationTest::config);
        var taskProgress = new PostgresTaskCompletionRepository(PostgresAdapterMigrationTest::config);
        try {
            challengeProgress.tierOf(UUID.randomUUID(), "challenge");
            taskProgress.isCompleted(UUID.randomUUID(), "challenge", "task");

            assertThat(tableExists("challenge_tier"))
                    .as("challenge_tier must be created by the challenge progress migrations")
                    .isTrue();
            assertThat(tableExists("task_completion"))
                    .as("task_completion must be created by the task progress migrations")
                    .isTrue();
            assertThat(tableExists("flyway_history_challenge_progress"))
                    .as("challenge progress must record its migrations in flyway_history_challenge_progress")
                    .isTrue();
            assertThat(tableExists("flyway_history_task_progress"))
                    .as("task progress must record its migrations in flyway_history_task_progress")
                    .isTrue();
        } finally {
            challengeProgress.close();
            taskProgress.close();
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                ResultSet rows = connection
                        .getMetaData()
                        .getTables(null, "public", table, new String[] {"TABLE"})) {
            return rows.next();
        }
    }
}
