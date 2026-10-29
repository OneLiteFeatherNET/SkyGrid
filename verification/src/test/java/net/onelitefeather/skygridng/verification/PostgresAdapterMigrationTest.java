package net.onelitefeather.skygridng.verification;

import net.onelitefeather.skygridng.challenge.PostgresChallengeProgressRepository;
import net.onelitefeather.skygridng.persistence.Database;
import net.onelitefeather.skygridng.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.challenge.PostgresTaskCompletionRepository;
import net.onelitefeather.skygridng.challenge.ProgressScope;
import net.onelitefeather.skygridng.season.SeasonId;
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
 * runs them: one {@link Database}, one pool, one migration history. Each test builds its own database, so no
 * state is shared between tests.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresAdapterMigrationTest {

    private static final SeasonId LEGACY = SeasonId.LEGACY;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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

    /** Runs the shared migrations up to {@code target}, so rows can be written between two migration steps. */
    private static void migrateTo(DatabaseConfig config, String target) {
        Flyway.configure()
                .dataSource(jdbcUrl(config), config.user(), config.password())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    /** Both progress repositories work against one shared database through one {@link Database}. */
    @Test
    void repositoriesShareOneDatabaseAndMigrateIt() throws SQLException {
        var config = freshDatabase();
        var database = new Database(() -> config);
        try {
            var challengeProgress = new PostgresChallengeProgressRepository(database);
            var taskProgress = new PostgresTaskCompletionRepository(database);
            assertThatCode(() -> {
                        challengeProgress.tierOf(LEGACY, UUID.randomUUID(), "challenge");
                        taskProgress.isCompleted(
                                new ProgressScope(LEGACY, UUID.randomUUID(), "challenge", 0), "task");
                    })
                    .as("challenge progress and task progress migrations must both apply to one database")
                    .doesNotThrowAnyException();
        } finally {
            database.close();
        }
    }

    /** One migration history for the whole database, and none of the previous per-unit history tables. */
    @Test
    void allMigrationsAreRecordedInOneHistoryTable() throws SQLException {
        var config = freshDatabase();
        var database = new Database(() -> config);
        try {
            new PostgresChallengeProgressRepository(database).tierOf(LEGACY, UUID.randomUUID(), "challenge");

            assertThat(tableExists(config, "challenge_tier"))
                    .as("challenge_tier must be created by the shared migrations")
                    .isTrue();
            assertThat(tableExists(config, "task_completion"))
                    .as("task_completion must be created by the shared migrations")
                    .isTrue();
            assertThat(tableExists(config, "flyway_schema_history"))
                    .as("every migration must be recorded in flyway_schema_history")
                    .isTrue();
            assertThat(tableExists(config, "flyway_history_challenge_progress"))
                    .as("the per-unit challenge history table must not be created any more")
                    .isFalse();
        } finally {
            database.close();
        }
    }

    /**
     * A database that already holds pre-season progress must come out of the season migrations with every row
     * in season {@code legacy} at prestige step 0, and with the same number of rows as before. The rows are
     * written between the migration steps that change their shape: challenge V1 to V2, then task V4, then the
     * season scoping of each table.
     */
    @Test
    void preSeasonRowsLandInLegacySeasonWithUnchangedRowCount() throws SQLException {
        var database = freshDatabase();
        migrateTo(database, "2");
        var playerA = UUID.randomUUID();
        var playerB = UUID.randomUUID();
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO challenge_tier (player_id, challenge_id, tier) VALUES "
                    + "('" + playerA + "', 'one_life', 3), ('" + playerB + "', 'one_life', 1)");
        }
        migrateTo(database, "4");
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO task_completion (player_id, challenge_id, task_id) VALUES "
                    + "('" + playerA + "', 'one_life', 'survive_a_day'), "
                    + "('" + playerA + "', 'one_life', 'survive_a_week'), "
                    + "('" + playerB + "', 'no_crafting_table', 'round_one')");
        }
        migrateTo(database, "5");

        var shared = new Database(() -> database);
        try {
            assertThat(new PostgresChallengeProgressRepository(shared).tierOf(LEGACY, playerA, "one_life"))
                    .as("the pre-season tier must be read back under the legacy season")
                    .isEqualTo(3);
            assertThat(new PostgresTaskCompletionRepository(shared)
                    .isCompleted(new ProgressScope(LEGACY, playerA, "one_life", 0), "survive_a_day"))
                    .as("the pre-season completion must be read back under the legacy season, step 0")
                    .isTrue();
        } finally {
            shared.close();
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

    private static boolean tableExists(DatabaseConfig config, String table) throws SQLException {
        try (Connection connection = connect(config);
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
