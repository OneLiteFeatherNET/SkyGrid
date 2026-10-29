package net.onelitefeather.skygridng.adapters.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The one Postgres database of this plugin: one HikariCP pool, one Flyway migration history
 * ({@code flyway_schema_history}) and one Jakarta Persistence (JSR 338) unit ({@code skygrid}, see
 * {@code META-INF/persistence.xml}) shared by every repository. Each repository receives this
 * instance and runs its queries through {@link #read} or {@link #inTransaction}.
 *
 * <p>Construction is cheap and side effect free: the {@code configSupplier} is not invoked, no pool is
 * opened and no migration runs until the first call to {@link #warmUp}, {@link #read} or
 * {@link #inTransaction}. That first call resolves the config, runs the migration and builds the
 * {@link EntityManagerFactory} under a lock, so concurrent first calls initialize exactly once.
 *
 * <p>An existing database from the previous layout (one history table per persistence unit,
 * {@code flyway_history_challenge_progress}, {@code flyway_history_task_progress},
 * {@code flyway_history_season}) is baselined once at version 7, after checking that each of those
 * histories ends at its expected version. A partially migrated database is refused with an
 * {@link IllegalStateException} and is not migrated. Migration V8 then drops the three old tables.
 *
 * <p>Bootstrap failures are wrapped as {@link PersistenceException}. Query and transaction failures
 * are left unwrapped, so a repository can still catch a specific Hibernate exception (for example
 * {@code ConstraintViolationException}) and decide to retry.
 *
 * <p>{@code jakarta.persistence}, Hibernate, HikariCP and Flyway types stay inside this module.
 */
public final class Database implements AutoCloseable {

    static final String PERSISTENCE_UNIT = "skygrid";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String LEGACY_BASELINE_VERSION = "7";

    /** The per-unit history tables of the previous layout, each with the version it must end at. */
    private static final Map<String, String> LEGACY_HISTORY_VERSIONS = Map.of(
            "flyway_history_challenge_progress", "3",
            "flyway_history_task_progress", "2",
            "flyway_history_season", "2");

    /** One database serves every repository, so the pool stays small: a Minecraft server makes few, short queries. */
    private static final int POOL_MAXIMUM_SIZE = 6;
    private static final int POOL_MINIMUM_IDLE = 1;

    private final Supplier<DatabaseConfig> configSupplier;
    private volatile EntityManagerFactory entityManagerFactory;

    /**
     * @param configSupplier supplies the connection settings on first use, normally
     *                       {@code DatabaseConfig::fromEnvironment}.
     */
    public Database(Supplier<DatabaseConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    /**
     * Returns the built {@link EntityManagerFactory}, initializing it on the first call only. A cheap
     * unsynchronized read serves every later call; the synchronized block makes concurrent first calls
     * run the migration and build the factory exactly once. Flyway and the JPA provider find their
     * plugins through the thread context class loader, which on a Paper server is the server's loader
     * and cannot see this plugin's jar, so both run with this class's loader set as the context class
     * loader, and the previous one is restored afterwards.
     */
    private EntityManagerFactory entityManagerFactory() {
        var existing = entityManagerFactory;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (entityManagerFactory == null) {
                var config = configSupplier.get();
                var previousContextClassLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(Database.class.getClassLoader());
                try {
                    migrate(config);
                    entityManagerFactory = createEntityManagerFactory(config);
                } finally {
                    Thread.currentThread().setContextClassLoader(previousContextClassLoader);
                }
            }
            return entityManagerFactory;
        }
    }

    private static void migrate(DatabaseConfig config) {
        Flyway flyway;
        try {
            flyway = Flyway.configure()
                    .dataSource(config.jdbcUrl(), config.user(), config.password())
                    .locations(MIGRATION_LOCATION)
                    // An existing database has the old tables but no flyway_schema_history yet. Baselining
                    // it at 7 records the old migrations as applied, so only V8 runs. A new, empty
                    // database is not baselined and runs V1 to V8.
                    .baselineOnMigrate(true)
                    .baselineVersion(LEGACY_BASELINE_VERSION)
                    .load();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to migrate the " + PERSISTENCE_UNIT + " schema", e);
        }
        // The check uses the same DataSource as the migration, so both reach the database the same way.
        requireExpectedLegacyHistories(flyway.getConfiguration().getDataSource());
        try {
            flyway.migrate();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to migrate the " + PERSISTENCE_UNIT + " schema", e);
        }
    }

    /**
     * Refuses a database whose old per-unit histories exist but do not all end at their expected version,
     * so a partially migrated database is never baselined. A database without any old history table is
     * fresh or already on the new layout, and passes.
     *
     * @throws IllegalStateException if the old layout is only partly migrated
     */
    private static void requireExpectedLegacyHistories(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            var present = LEGACY_HISTORY_VERSIONS.keySet().stream()
                    .filter(table -> tableExists(connection, table))
                    .toList();
            if (present.isEmpty()) {
                return;
            }
            for (var entry : LEGACY_HISTORY_VERSIONS.entrySet()) {
                var table = entry.getKey();
                if (!present.contains(table)) {
                    throw new IllegalStateException("Refusing to migrate: the old history table " + table
                            + " is missing while other old history tables exist. Restore the database from a"
                            + " backup, or migrate it with the previous release first.");
                }
                if (!endsAt(connection, table, entry.getValue())) {
                    throw new IllegalStateException("Refusing to migrate: " + table
                            + " does not end at its expected version " + entry.getValue()
                            + ". Migrate the database with the previous release first.");
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to inspect the legacy migration history", e);
        }
    }

    private static boolean tableExists(Connection connection, String table) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getString(1) != null;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to inspect table " + table, e);
        }
    }

    /** True when the latest recorded migration of {@code table} succeeded at {@code version}. Table names are constants. */
    private static boolean endsAt(Connection connection, String table, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, success FROM " + table + " ORDER BY installed_rank DESC LIMIT 1");
                ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean("success") && version.equals(rows.getString("version"));
        }
    }

    /**
     * Builds the entity manager factory through the standard
     * {@link Persistence#createEntityManagerFactory(String, Map)} entry point, overriding the static
     * {@code persistence.xml} with the runtime connection settings: Hibernate's HikariCP connection
     * provider owns the single pool, fed with the {@code hibernate.hikari.*} properties.
     */
    private static EntityManagerFactory createEntityManagerFactory(DatabaseConfig config) {
        var overrides = Map.<String, Object>of(
                "hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider",
                "hibernate.hikari.jdbcUrl", config.jdbcUrl(),
                "hibernate.hikari.dataSource.user", config.user(),
                "hibernate.hikari.dataSource.password", config.password(),
                "hibernate.hikari.poolName", "skygridng",
                "hibernate.hikari.maximumPoolSize", String.valueOf(POOL_MAXIMUM_SIZE),
                "hibernate.hikari.minimumIdle", String.valueOf(POOL_MINIMUM_IDLE));
        try {
            return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT, overrides);
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to build the " + PERSISTENCE_UNIT + " entity manager factory", e);
        }
    }

    /**
     * Runs the lazy bootstrap now (config, migration, entity manager factory) instead of on the first
     * query, so a caller can pay that cost off the main thread. A failure is thrown to the caller and
     * the bootstrap is retried on the next use.
     */
    public void warmUp() {
        entityManagerFactory();
    }

    /**
     * Runs {@code work} against a fresh {@link EntityManager} without a transaction, for read-only
     * queries. The manager is closed afterwards; exceptions propagate unwrapped.
     *
     * @param work the query, given the open {@link EntityManager}
     * @param <R>  the result type
     * @return whatever {@code work} returns
     */
    public <R> R read(Function<EntityManager, R> work) {
        try (var entityManager = entityManagerFactory().createEntityManager()) {
            return work.apply(entityManager);
        }
    }

    /**
     * Runs {@code work} against a fresh {@link EntityManager} inside its own transaction and commits on
     * success. If {@code work} throws, the transaction is rolled back (when still active) and the
     * original exception is rethrown unmodified, so a repository can catch a specific Hibernate exception.
     *
     * @param work the unit of work, given the open, transactional {@link EntityManager}
     * @param <R>  the result type
     * @return whatever {@code work} returns, after the transaction commits
     */
    public <R> R inTransaction(Function<EntityManager, R> work) {
        try (var entityManager = entityManagerFactory().createEntityManager()) {
            var transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                var result = work.apply(entityManager);
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    // A failed rollback must never replace the original exception, which a repository may inspect.
                    try {
                        transaction.rollback();
                    } catch (RuntimeException rollbackFailure) {
                        e.addSuppressed(rollbackFailure);
                    }
                }
                throw e;
            }
        }
    }

    /**
     * Closes the entity manager factory and with it the HikariCP pool, if it was ever built. Call once,
     * on plugin disable. A no-op when the database was never used.
     */
    @Override
    public void close() {
        var existing = entityManagerFactory;
        if (existing != null) {
            existing.close();
        }
    }
}
