package net.onelitefeather.skygridng.adapters.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared, reusable base for every Postgres-backed, Jakarta Persistence (JSR 338) via Hibernate ORM
 * repository in this project. This is the generic factory-bootstrap and transaction-lifecycle
 * plumbing that {@code PostgresChallengeProgressRepository} (in {@code adapters/persistence})
 * originally implemented by hand for the {@code challenge_tier} table alone; every future
 * Postgres-backed adapter (for example an analytics store) should extend this class instead of
 * writing a second copy of the same bootstrap. A concrete subclass needs only its own {@code
 * @Entity} class, a small repository implementing the relevant {@code ports.out} interface on top
 * of {@link #read} and {@link #inTransaction}, and one persistence-unit entry in its own {@code
 * META-INF/persistence.xml}.
 *
 * <p>Construction is cheap and side effect free: the {@code configSupplier} passed to the
 * constructor is not invoked, no connection pool is opened, and no Flyway migration runs until the
 * first call to {@link #read} or {@link #inTransaction}. That first call resolves the config, runs
 * the migration, and then builds the {@link EntityManagerFactory} (under a double-checked lock, so
 * concurrent first calls still initialize exactly once); every later call reuses the already-built
 * factory without touching the supplier, Flyway, or {@link Persistence} again. This keeps a Guice
 * {@code Stage.PRODUCTION} eager singleton binding of a subclass cheap to construct: an environment
 * that never configures a database, or a test that never exercises the repository, pays nothing for
 * a connection attempt it never asked for.
 *
 * <p>Bootstrap failures (a Flyway migration error, or a failure building the entity manager
 * factory) are wrapped as the standard {@link PersistenceException} (JSR 338's own runtime
 * exception type for exactly this purpose), never a bespoke per-adapter exception class. Query and
 * transaction failures raised by {@link #read} or {@link #inTransaction} are deliberately left
 * unwrapped: Hibernate's own exceptions (for example {@code ConstraintViolationException}) are
 * already subclasses of {@link PersistenceException}, and a subclass may need to catch one of those
 * specific types itself (a pessimistic-lock-plus-retry strategy, for instance) to make a decision
 * that this base class has no way to know about. Wrapping here would hide that type from the
 * subclass and defeat the retry.
 *
 * <p>{@code jakarta.persistence}, Hibernate, HikariCP and Flyway types must never appear in {@code
 * api}, {@code ports}, or {@code core}; they stay inside this module and the adapters that depend on
 * it. Every query goes through the {@link EntityManager}, so no {@code java.sql} statement is ever
 * built by concatenating or interpolating a caller supplied value.
 */
public abstract class AbstractJpaRepository implements AutoCloseable {

    private final String persistenceUnitName;
    private final String migrationLocation;
    private final String migrationHistoryTable;
    private final Supplier<DatabaseConfig> configSupplier;
    private volatile EntityManagerFactory entityManagerFactory;

    /**
     * Stores the persistence unit name, the Flyway classpath migration location, the Flyway history
     * table, and {@code configSupplier} for later use. Nothing is resolved, connected, or migrated
     * yet: that all happens lazily, on the first call to {@link #read} or {@link #inTransaction}.
     *
     * @param persistenceUnitName    the {@code <persistence-unit name="...">} declared in the
     *                               subclass's own {@code META-INF/persistence.xml}
     * @param migrationLocation      the Flyway classpath migration location for this persistence
     *                               unit, for example {@code "classpath:db/migration/task-progress"}.
     *                               Each adapter gets its own subfolder, so two adapters bundled into
     *                               one jar never see each other's {@code V1__} scripts.
     * @param migrationHistoryTable  the Flyway history table for this persistence unit, for example
     *                               {@code "flyway_history_task_progress"}. Each adapter gets its own
     *                               table, so a second adapter's migrations are not checked against
     *                               the first adapter's recorded versions.
     * @param configSupplier         supplies the connection settings on first use, normally
     *                               {@code DatabaseConfig::fromEnvironment}. Deferring this to a
     *                               supplier (rather than accepting a resolved {@link DatabaseConfig})
     *                               means the required-environment-variable check inside
     *                               {@link DatabaseConfig#fromEnvironment()} is not run at construction
     *                               time either.
     */
    protected AbstractJpaRepository(
            String persistenceUnitName,
            String migrationLocation,
            String migrationHistoryTable,
            Supplier<DatabaseConfig> configSupplier) {
        this.persistenceUnitName = persistenceUnitName;
        this.migrationLocation = migrationLocation;
        this.migrationHistoryTable = migrationHistoryTable;
        this.configSupplier = configSupplier;
    }

    /**
     * Returns the built {@link EntityManagerFactory}, initializing it on the first call only. Uses
     * double-checked locking on {@code this}: a cheap unsynchronized read handles every call after
     * the first, and the synchronized block guarantees that concurrent first calls run the migration
     * and build the factory exactly once. The Flyway migration always runs first and against its own
     * short-lived, unpooled JDBC connection (Flyway needs a data source regardless of Hibernate); if
     * it fails, nothing is left open and {@link #entityManagerFactory} is never assigned, so a bad
     * migration or a briefly unreachable Postgres does not leak a connection pool on every retry.
     * Flyway and the JPA provider find their plugins through the thread context class loader, which
     * on a Paper server is the server's loader and cannot see this plugin's jar, so both run with
     * this class's loader set as the context class loader and the previous one is restored after.
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
                Thread.currentThread().setContextClassLoader(AbstractJpaRepository.class.getClassLoader());
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

    private void migrate(DatabaseConfig config) {
        try {
            Flyway.configure()
                    .dataSource(config.jdbcUrl(), config.user(), config.password())
                    .locations(migrationLocation)
                    .table(migrationHistoryTable)
                    // Both adapters share the public schema, so the second adapter to start finds
                    // tables it did not create and no history table of its own. Baselining that
                    // history table at version 0 lets its V1 still apply instead of failing.
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to migrate the " + persistenceUnitName + " schema", e);
        }
    }

    /**
     * Builds the entity manager factory through the standard
     * {@link Persistence#createEntityManagerFactory(String, Map)} entry point, overriding the
     * subclass's {@code persistence.xml} with the connection settings from {@code config}:
     * Hibernate's own HikariCP connection provider owns the pool ({@code
     * hibernate.connection.provider_class}), fed with the Hikari specific {@code hibernate.hikari.*}
     * properties. These values are only known at runtime (read from environment variables via
     * {@link DatabaseConfig}), so they cannot live in the static {@code persistence.xml} file. The
     * Hikari pool name is derived from {@code persistenceUnitName} so that concurrently running
     * persistence units (one per adapter) each get a distinguishable pool name.
     */
    private EntityManagerFactory createEntityManagerFactory(DatabaseConfig config) {
        var overrides = Map.<String, Object>of(
                "hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider",
                "hibernate.hikari.jdbcUrl", config.jdbcUrl(),
                "hibernate.hikari.dataSource.user", config.user(),
                "hibernate.hikari.dataSource.password", config.password(),
                "hibernate.hikari.poolName", "skygridng-" + persistenceUnitName);
        try {
            return Persistence.createEntityManagerFactory(persistenceUnitName, overrides);
        } catch (RuntimeException e) {
            throw new PersistenceException(
                    "Failed to build the " + persistenceUnitName + " entity manager factory", e);
        }
    }

    /**
     * Runs {@code work} against a fresh {@link EntityManager} with no transaction, for read-only
     * queries. The {@link EntityManager} is opened and closed around the call (try-with-resources);
     * any exception {@code work} throws propagates unwrapped, since Hibernate's own exceptions are
     * already {@link PersistenceException} subclasses and a caller may need to inspect one
     * specifically.
     *
     * @param work the query, given the open {@link EntityManager}
     * @param <R>  the result type
     * @return whatever {@code work} returns
     */
    protected <R> R read(Function<EntityManager, R> work) {
        try (var entityManager = entityManagerFactory().createEntityManager()) {
            return work.apply(entityManager);
        }
    }

    /**
     * Runs {@code work} against a fresh {@link EntityManager} inside its own transaction: begins the
     * transaction, calls {@code work}, and commits on success. If {@code work} throws, the
     * transaction is rolled back (only if still active) and the original exception is rethrown
     * unmodified, never wrapped or swallowed, so a subclass can catch a specific Hibernate exception
     * (for example {@code ConstraintViolationException}) around its own call to this method to
     * implement its own retry policy.
     *
     * @param work the unit of work, given the open, transactional {@link EntityManager}
     * @param <R>  the result type
     * @return whatever {@code work} returns, after the transaction commits
     */
    protected <R> R inTransaction(Function<EntityManager, R> work) {
        try (var entityManager = entityManagerFactory().createEntityManager()) {
            var transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                var result = work.apply(entityManager);
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    // A failure here (for example the connection dropped mid-rollback) must never
                    // replace the original exception: a subclass (see
                    // PostgresChallengeProgressRepository.recordCompletionAttempt) inspects e's own
                    // cause chain for a specific Hibernate exception to decide whether to retry, so
                    // losing e in favor of the rollback failure would silently break that decision.
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
     * Closes the entity manager factory (and with it, Hibernate's HikariCP pool), if one was ever
     * built. Call once, on plugin disable. Safe to call even if no method was ever invoked (the
     * factory was never initialized), in which case this is a no-op rather than a null pointer
     * exception.
     */
    @Override
    public void close() {
        var existing = entityManagerFactory;
        if (existing != null) {
            existing.close();
        }
    }
}
