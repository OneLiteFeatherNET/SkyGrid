package net.onelitefeather.skygridng.adapters.challengeprogress;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import org.flywaydb.core.Flyway;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PostgreSQL-backed {@link ChallengeProgressRepository}. Connections are pooled with HikariCP;
 * every query is a parameterized {@link java.sql.PreparedStatement}, never a string built by
 * concatenating or interpolating a caller supplied value.
 *
 * <p>The schema is owned by the Flyway migration bundled at
 * {@code classpath:db/migration/V1__create_challenge_tier.sql}, never a hand-edited
 * {@code CREATE TABLE} assumed to match this class.
 *
 * <p>Construction is cheap and side effect free: the {@code configSupplier} passed to the
 * constructor is not invoked, no HikariCP pool is opened, and no Flyway migration runs until the
 * first actual call to {@link #tierOf}, {@link #recordCompletion}, or {@link #resetAll}. That
 * first call resolves the config, builds the pool, and runs the migration before serving its
 * query (under a double-checked lock, so concurrent first calls still initialize exactly once);
 * every later call reuses the already-initialized pool without touching the supplier or Flyway
 * again. This keeps a Guice {@code Stage.PRODUCTION} eager singleton binding of this class cheap
 * to construct: an environment that never configures a database, or a test that never exercises
 * this repository, pays nothing for a connection attempt it never asked for. The invariant that
 * the migration always runs before the pool serves its first query still holds, it just happens
 * lazily on first use rather than eagerly at construction.
 *
 * <p>This is the only place in the project that touches {@code java.sql}, HikariCP or Flyway types;
 * they must never appear in {@code api}, {@code ports}, or {@code core}. The first call to any
 * method does blocking I/O (opening a pool, running a migration); callers should expect that cost
 * once, on whichever call happens first.
 */
public final class PostgresChallengeProgressRepository implements ChallengeProgressRepository, AutoCloseable {

    private static final String SELECT_TIER = """
            SELECT tier FROM challenge_tier WHERE player_id = ? AND challenge_id = ?
            """;

    // Upsert: creates the row at tier 1 on first completion, otherwise increments the stored tier by
    // one, in a single round trip. RETURNING hands back the resulting value so no follow-up SELECT
    // is needed.
    private static final String UPSERT_TIER = """
            INSERT INTO challenge_tier (player_id, challenge_id, tier)
            VALUES (?, ?, 1)
            ON CONFLICT (player_id, challenge_id)
            DO UPDATE SET tier = challenge_tier.tier + 1
            RETURNING tier
            """;

    // DELETE, not TRUNCATE: TRUNCATE takes an ACCESS EXCLUSIVE lock on the whole table, which would
    // block every concurrent tierOf/recordCompletion call (including a player finishing a challenge
    // mid reset) for the statement's whole duration, and some poolers/replication setups treat it
    // less predictably than a plain DML statement. DELETE takes a lighter ROW EXCLUSIVE lock, is
    // fully transactional and MVCC-visible like any other write, and this table is small (one row
    // per player per challenge), so DELETE's per-row cost is not a concern here. Revisit only if
    // this table ever grows large enough that the lock trade-off flips.
    private static final String DELETE_ALL = """
            DELETE FROM challenge_tier
            """;

    private final Supplier<DatabaseConfig> configSupplier;
    private volatile HikariDataSource dataSource;

    /**
     * Stores {@code configSupplier} for later use. Nothing is resolved, connected, or migrated
     * yet: that all happens lazily, on the first call to {@link #tierOf}, {@link #recordCompletion},
     * or {@link #resetAll}.
     *
     * @param configSupplier supplies the connection settings on first use, normally
     *                       {@code DatabaseConfig::fromEnvironment}. Deferring this to a supplier
     *                       (rather than accepting a resolved {@link DatabaseConfig}) means the
     *                       required-environment-variable check inside
     *                       {@link DatabaseConfig#fromEnvironment()} is not run at construction
     *                       time either.
     */
    public PostgresChallengeProgressRepository(Supplier<DatabaseConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    /**
     * Returns the pooled, migrated data source, initializing it on the first call only. Uses
     * double-checked locking on {@code this}: a cheap unsynchronized read handles every call after
     * the first, and the synchronized block guarantees that concurrent first calls (for example two
     * players completing a challenge at nearly the same moment) build and migrate the pool exactly
     * once. If the Flyway migration fails, the pool just created for that attempt is closed before
     * the failure is reported, so a bad migration or a briefly unreachable Postgres does not leak a
     * connection pool and its housekeeping threads on every retry; the volatile field is only ever
     * assigned once migration has actually succeeded.
     */
    private HikariDataSource dataSource() {
        var existing = dataSource;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (dataSource == null) {
                var created = createDataSource(configSupplier.get());
                try {
                    Flyway.configure()
                            .dataSource(created)
                            .locations("classpath:db/migration")
                            .load()
                            .migrate();
                } catch (RuntimeException e) {
                    created.close();
                    throw new ChallengeProgressPersistenceException(
                            "Failed to migrate the challenge progress schema", e);
                }
                dataSource = created;
            }
            return dataSource;
        }
    }

    private static HikariDataSource createDataSource(DatabaseConfig config) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.user());
        hikariConfig.setPassword(config.password());
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setPoolName("skygridng-challenge-progress");
        return new HikariDataSource(hikariConfig);
    }

    @Override
    public int tierOf(UUID playerId, String challengeId) {
        try (var connection = dataSource().getConnection();
             var statement = connection.prepareStatement(SELECT_TIER)) {
            statement.setObject(1, playerId);
            statement.setString(2, challengeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("tier") : 0;
            }
        } catch (SQLException e) {
            throw new ChallengeProgressPersistenceException(
                    "Failed to read the challenge tier for player " + playerId + ", challenge " + challengeId, e);
        }
    }

    @Override
    public int recordCompletion(UUID playerId, String challengeId) {
        try (var connection = dataSource().getConnection();
             var statement = connection.prepareStatement(UPSERT_TIER)) {
            statement.setObject(1, playerId);
            statement.setString(2, challengeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new ChallengeProgressPersistenceException(
                            "Upsert of the challenge tier returned no row for player " + playerId
                                    + ", challenge " + challengeId, null);
                }
                return resultSet.getInt("tier");
            }
        } catch (SQLException e) {
            throw new ChallengeProgressPersistenceException(
                    "Failed to record a challenge completion for player " + playerId + ", challenge " + challengeId, e);
        }
    }

    @Override
    public void resetAll() {
        try (var connection = dataSource().getConnection();
             var statement = connection.prepareStatement(DELETE_ALL)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ChallengeProgressPersistenceException("Failed to reset all challenge progress", e);
        }
    }

    /**
     * Shuts down the connection pool, if one was ever built. Call once, on plugin disable. Safe to
     * call even if this repository's methods were never invoked (the pool was never initialized),
     * in which case this is a no-op rather than a null pointer exception.
     */
    @Override
    public void close() {
        var existing = dataSource;
        if (existing != null) {
            existing.close();
        }
    }
}
