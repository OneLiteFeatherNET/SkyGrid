package net.onelitefeather.skygridng.persistence;

import net.onelitefeather.skygridng.challenge.PostgresChallengeProgressRepository;
import net.onelitefeather.skygridng.season.PostgresSeasonRunRepository;
import net.onelitefeather.skygridng.challenge.PostgresTaskCompletionRepository;
import net.onelitefeather.skygridng.challenge.ProgressScope;
import net.onelitefeather.skygridng.season.SeasonId;
import net.onelitefeather.skygridng.season.SeasonPhase;
import net.onelitefeather.skygridng.season.SeasonRun;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration behaviour of the shared {@link Database} against real PostgreSQL: a fresh database, an
 * existing database from the previous layout (three history tables), and a partly migrated one that
 * must be refused. Each test runs against its own fresh database and its own temporary folder.
 */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationTest {

    private static final SeasonId SEASON = new SeasonId("s01");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    /** The three per-unit history tables of the previous layout. */
    private static final String CHALLENGE_HISTORY = "flyway_history_challenge_progress";
    private static final String TASK_HISTORY = "flyway_history_task_progress";
    private static final String SEASON_HISTORY = "flyway_history_season";

    /**
     * The previous layout's scripts, as {old file name, current file name}: the same SQL, renumbered only.
     * Challenge keeps V1 to V3, task was V1 and V2 (now V4 and V5), season was V1 and V2 (now V6 and V7).
     */
    private static final String[][] CHALLENGE_SCRIPTS = {
            {"V1__create_challenge_tier.sql", "V1__create_challenge_tier.sql"},
            {"V2__bound_challenge_id_length.sql", "V2__bound_challenge_id_length.sql"},
            {"V3__scope_tier_by_season.sql", "V3__scope_tier_by_season.sql"},
    };
    private static final String[][] TASK_SCRIPTS = {
            {"V1__create_task_completion.sql", "V4__create_task_completion.sql"},
            {"V2__scope_completion_by_season_and_step.sql", "V5__scope_completion_by_season_and_step.sql"},
    };
    private static final String[][] SEASON_SCRIPTS = {
            {"V1__create_season_run.sql", "V6__create_season_run.sql"},
            {"V2__add_postponed_end_and_announcements.sql", "V7__add_postponed_end_and_announcements.sql"},
    };

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    Path folder;

    @Test
    void aFreshDatabaseMigratesToVersion8AndAllRepositoriesWorkThroughOneDatabase() throws SQLException {
        var config = FreshTestDatabase.create(POSTGRES);
        var database = new Database(() -> config);
        try {
            var playerId = UUID.randomUUID();
            var challenges = new PostgresChallengeProgressRepository(database);
            var tasks = new PostgresTaskCompletionRepository(database);
            var seasons = new PostgresSeasonRunRepository(database, CLOCK);

            assertThat(challenges.recordCompletion(SEASON, playerId, "one_life")).isEqualTo(1);
            assertThat(tasks.markCompleted(new ProgressScope(SEASON, playerId, "one_life", 0), "survive_a_day"))
                    .isTrue();
            var run = new SeasonRun(SEASON, "skygridng:season_s01", 42L, SeasonPhase.PREPARED, 0, null, null);
            seasons.save(run);
            assertThat(seasons.find(SEASON)).contains(run);

            assertThat(appliedVersions(config))
                    .as("a new database runs V1 to V8 and records each one in flyway_schema_history")
                    .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
            assertThat(tableExists(config, CHALLENGE_HISTORY)).isFalse();
            assertThat(tableExists(config, TASK_HISTORY)).isFalse();
            assertThat(tableExists(config, SEASON_HISTORY)).isFalse();
        } finally {
            database.close();
        }
    }

    @Test
    void anExistingOldLayoutIsBaselinedAtSevenAndOnlyV8RunsAndDropsTheOldHistoryTables() throws SQLException, IOException {
        var config = FreshTestDatabase.create(POSTGRES);
        migrateOldLayout(config, CHALLENGE_SCRIPTS, CHALLENGE_HISTORY, null);
        migrateOldLayout(config, TASK_SCRIPTS, TASK_HISTORY, null);
        migrateOldLayout(config, SEASON_SCRIPTS, SEASON_HISTORY, null);
        var playerId = UUID.randomUUID();
        insertLegacyRows(config, playerId);
        var database = new Database(() -> config);
        try {
            database.warmUp();

            assertThat(appliedVersions(config))
                    .as("the old layout is baselined at 7, so only V8 is recorded as applied")
                    .containsExactly("7", "8");
            assertThat(tableExists(config, CHALLENGE_HISTORY)).isFalse();
            assertThat(tableExists(config, TASK_HISTORY)).isFalse();
            assertThat(tableExists(config, SEASON_HISTORY)).isFalse();

            assertThat(new PostgresChallengeProgressRepository(database).tierOf(SeasonId.LEGACY, playerId, "one_life"))
                    .as("the pre-existing tier must survive the upgrade")
                    .isEqualTo(3);
            assertThat(new PostgresTaskCompletionRepository(database)
                    .isCompleted(new ProgressScope(SeasonId.LEGACY, playerId, "one_life", 0), "survive_a_day"))
                    .as("the pre-existing completion must survive the upgrade")
                    .isTrue();
            assertThat(new PostgresSeasonRunRepository(database, CLOCK).find(SEASON))
                    .as("the pre-existing season run must survive the upgrade")
                    .isPresent();
        } finally {
            database.close();
        }
    }

    @Test
    void aPartiallyMigratedOldLayoutIsRefusedAndNotBaselined() throws SQLException, IOException {
        var config = FreshTestDatabase.create(POSTGRES);
        migrateOldLayout(config, CHALLENGE_SCRIPTS, CHALLENGE_HISTORY, "2");
        migrateOldLayout(config, TASK_SCRIPTS, TASK_HISTORY, null);
        migrateOldLayout(config, SEASON_SCRIPTS, SEASON_HISTORY, null);
        var database = new Database(() -> config);
        try {
            assertThatThrownBy(database::warmUp)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(CHALLENGE_HISTORY);

            assertThat(tableExists(config, "flyway_schema_history"))
                    .as("a refused database must not get a baseline or any new migration")
                    .isFalse();
            assertThat(tableExists(config, CHALLENGE_HISTORY))
                    .as("the old history tables must be left alone when the upgrade is refused")
                    .isTrue();
        } finally {
            database.close();
        }
    }

    /**
     * Creates the previous layout of one persistence unit by running Flyway with that unit's own history
     * table over the same SQL, copied into a temporary folder under its old file name.
     */
    private void migrateOldLayout(DatabaseConfig config, String[][] scripts, String history, String target)
            throws IOException {
        var unitFolder = Files.createDirectories(folder.resolve(history));
        for (var script : scripts) {
            try (InputStream content = DatabaseMigrationTest.class.getResourceAsStream(
                    "/db/migration/" + script[1])) {
                assertThat(content).as("current script " + script[1]).isNotNull();
                Files.copy(content, unitFolder.resolve(script[0]));
            }
        }
        var flyway = Flyway.configure()
                .dataSource(jdbcUrl(config), config.user(), config.password())
                .locations("filesystem:" + unitFolder.toAbsolutePath())
                .table(history)
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (target != null) {
            flyway.target(target);
        }
        flyway.load().migrate();
    }

    /** Pre-existing rows, written the way the previous layout stored them, in the legacy season. */
    private static void insertLegacyRows(DatabaseConfig config, UUID playerId) throws SQLException {
        try (Connection connection = connect(config); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO challenge_tier (season_id, player_id, challenge_id, tier) VALUES "
                    + "('legacy', '" + playerId + "', 'one_life', 3)");
            statement.executeUpdate("INSERT INTO task_completion "
                    + "(season_id, player_id, challenge_id, prestige_step, task_id) VALUES "
                    + "('legacy', '" + playerId + "', 'one_life', 0, 'survive_a_day')");
            statement.executeUpdate("INSERT INTO season_run (season_id, world_key, seed, phase, updated_at) VALUES "
                    + "('s01', 'skygridng:season_s01', 42, 'PREPARED', now())");
        }
    }

    private static List<String> appliedVersions(DatabaseConfig config) throws SQLException {
        try (Connection connection = connect(config);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")) {
            var versions = new ArrayList<String>();
            while (rows.next()) {
                versions.add(rows.getString(1));
            }
            return versions;
        }
    }

    private static boolean tableExists(DatabaseConfig config, String table) throws SQLException {
        try (Connection connection = connect(config);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT to_regclass('" + table + "')")) {
            rows.next();
            return rows.getString(1) != null;
        }
    }

    private static Connection connect(DatabaseConfig config) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(config), config.user(), config.password());
    }

    private static String jdbcUrl(DatabaseConfig config) {
        return "jdbc:postgresql://" + config.host() + ":" + config.port() + "/" + config.name();
    }
}
