package net.onelitefeather.skygridng.verification;

import net.onelitefeather.skygridng.adapters.challengeprogress.PostgresChallengeProgressRepository;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.adapters.taskprogress.PostgresTaskCompletionRepository;
import net.onelitefeather.skygridng.api.model.ProgressScope;
import net.onelitefeather.skygridng.api.model.SeasonId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Migration checks for the progress adapters against one Postgres database, the way a single plugin install
 * runs them. Each test builds its own repositories and, where it needs one, its own fresh database, so no
 * state is shared between tests.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresAdapterMigrationTest {

    private static final SeasonId LEGACY = SeasonId.LEGACY;

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

    /** Creates an empty database on the shared container and returns the settings to connect to it. */
    private static DatabaseConfig freshDatabase() throws SQLException {
        var name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Statement statement = adminConnection().createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        return new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                name,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String jdbcUrl(DatabaseConfig config) {
        return "jdbc:postgresql://" + config.host() + ":" + config.port() + "/" + config.name();
    }

    private static Connection connect(DatabaseConfig config) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(config), config.user(), config.password());
    }

    /** Runs one adapter's Flyway migrations up to {@code target}, the way that adapter's own bootstrap would. */
    private static void migrateTo(DatabaseConfig config, String location, String historyTable, String target) {
        Flyway.configure()
                .dataSource(jdbcUrl(config), config.user(), config.password())
                .locations(location)
                .table(historyTable)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .target(target)
                .load()
                .migrate();
    }

    /** Both adapters can run their migrations against one shared database without failing. */
    @Test
    void bothAdaptersMigrateTheSameDatabaseWithoutClashing() {
        var challengeProgress = new PostgresChallengeProgressRepository(PostgresAdapterMigrationTest::config);
        var taskProgress = new PostgresTaskCompletionRepository(PostgresAdapterMigrationTest::config);
        try {
            assertThatCode(() -> {
                        challengeProgress.tierOf(LEGACY, UUID.randomUUID(), "challenge");
                        taskProgress.isCompleted(
                                new ProgressScope(LEGACY, UUID.randomUUID(), "challenge", 0), "task");
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
            challengeProgress.tierOf(LEGACY, UUID.randomUUID(), "challenge");
            taskProgress.isCompleted(new ProgressScope(LEGACY, UUID.randomUUID(), "challenge", 0), "task");

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

    /**
     * A database that already holds pre-season progress (migrated only up to challenge V2 and task V1, with
     * rows written the way the previous release wrote them) must come out of the season migrations with every
     * row in season {@code legacy} at prestige step 0, and with the same number of rows as before.
     */
    @Test
    void preSeasonRowsLandInLegacySeasonWithUnchangedRowCount() throws SQLException {
        var database = freshDatabase();
        migrateTo(database, "classpath:db/migration/challenge-progress", "flyway_history_challenge_progress", "2");
        migrateTo(database, "classpath:db/migration/task-progress", "flyway_history_task_progress", "1");
        var playerA = UUID.randomUUID();
        var playerB = UUID.randomUUID();
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO challenge_tier (player_id, challenge_id, tier) VALUES "
                    + "('" + playerA + "', 'one_life', 3), ('" + playerB + "', 'one_life', 1)");
            statement.executeUpdate("INSERT INTO task_completion (player_id, challenge_id, task_id) VALUES "
                    + "('" + playerA + "', 'one_life', 'survive_a_day'), "
                    + "('" + playerA + "', 'one_life', 'survive_a_week'), "
                    + "('" + playerB + "', 'no_crafting_table', 'round_one')");
        }

        var challengeProgress = new PostgresChallengeProgressRepository(() -> database);
        var taskProgress = new PostgresTaskCompletionRepository(() -> database);
        try {
            assertThat(challengeProgress.tierOf(LEGACY, playerA, "one_life"))
                    .as("the pre-season tier must be read back under the legacy season")
                    .isEqualTo(3);
            assertThat(taskProgress.isCompleted(new ProgressScope(LEGACY, playerA, "one_life", 0), "survive_a_day"))
                    .as("the pre-season completion must be read back under the legacy season, step 0")
                    .isTrue();
        } finally {
            challengeProgress.close();
            taskProgress.close();
        }

        assertThat(countRowsBySeason(database, "challenge_tier"))
                .as("every pre-season tier row must stay, in season legacy, with the row count unchanged")
                .isEqualTo(Map.of("legacy", 2L));
        assertThat(countRowsBySeason(database, "task_completion"))
                .as("every pre-season completion row must stay, in season legacy, with the row count unchanged")
                .isEqualTo(Map.of("legacy", 3L));
        assertThat(countRows(database, "task_completion", "prestige_step <> 0"))
                .as("every migrated completion must be at prestige step 0")
                .isZero();
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = adminConnection();
                ResultSet rows = connection
                        .getMetaData()
                        .getTables(null, "public", table, new String[] {"TABLE"})) {
            return rows.next();
        }
    }

    private static Map<String, Long> countRowsBySeason(DatabaseConfig database, String table) throws SQLException {
        var counts = new LinkedHashMap<String, Long>();
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT season_id, count(*) FROM " + table + " GROUP BY season_id")) {
            while (rows.next()) {
                counts.put(rows.getString(1), rows.getLong(2));
            }
        }
        return counts;
    }

    private static long countRows(DatabaseConfig database, String table, String predicate) throws SQLException {
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table + " WHERE " + predicate)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
