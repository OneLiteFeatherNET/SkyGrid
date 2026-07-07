package net.onelitefeather.skygridng.adapters.challengeprogress;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.CriteriaDelete;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import org.flywaydb.core.Flyway;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PostgreSQL-backed {@link ChallengeProgressRepository}, persisted through Jakarta Persistence
 * (JSR 338) via Hibernate ORM, bootstrapped standalone with no Spring, Micronaut, or application
 * server: the standard {@link Persistence#createEntityManagerFactory(String, Map)} entry point is
 * the actual code path, not just {@code META-INF/persistence.xml} kept for decoration. Every query
 * goes through the {@link jakarta.persistence.EntityManager}, so no {@code java.sql} statement is
 * ever built by concatenating or interpolating a caller supplied value.
 *
 * <p>The schema is owned by the Flyway migration bundled at
 * {@code classpath:db/migration/V1__create_challenge_tier.sql}, never a hand-edited
 * {@code CREATE TABLE} assumed to match this class; Hibernate never creates or alters that schema
 * itself ({@code hibernate.hbm2ddl.auto=validate} in {@code persistence.xml}), it only validates the
 * {@link ChallengeTierEntity} mapping against the table Flyway already created.
 *
 * <p>Construction is cheap and side effect free: the {@code configSupplier} passed to the
 * constructor is not invoked, no connection pool is opened, and no Flyway migration runs until the
 * first actual call to {@link #tierOf}, {@link #recordCompletion}, or {@link #resetAll}. That first
 * call resolves the config, runs the migration, and then builds the {@link EntityManagerFactory}
 * (under a double-checked lock, so concurrent first calls still initialize exactly once); every
 * later call reuses the already-built factory without touching the supplier, Flyway, or
 * {@link Persistence} again. This keeps a Guice {@code Stage.PRODUCTION} eager singleton binding of
 * this class cheap to construct: an environment that never configures a database, or a test that
 * never exercises this repository, pays nothing for a connection attempt it never asked for.
 *
 * <p>This is the only place in the project that touches {@code jakarta.persistence}, Hibernate,
 * HikariCP or Flyway types; they must never appear in {@code api}, {@code ports}, or {@code core}.
 * The first call to any method does blocking I/O (running a migration, opening a pool, building the
 * entity manager factory); callers should expect that cost once, on whichever call happens first.
 */
public final class PostgresChallengeProgressRepository implements ChallengeProgressRepository, AutoCloseable {

    private static final String PERSISTENCE_UNIT_NAME = "challenge-progress";

    private final Supplier<DatabaseConfig> configSupplier;
    private volatile EntityManagerFactory entityManagerFactory;

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
     * Returns the built {@link EntityManagerFactory}, initializing it on the first call only. Uses
     * double-checked locking on {@code this}: a cheap unsynchronized read handles every call after
     * the first, and the synchronized block guarantees that concurrent first calls (for example two
     * players completing a challenge at nearly the same moment) run the migration and build the
     * factory exactly once. The Flyway migration always runs first and against its own short-lived,
     * unpooled JDBC connection (Flyway needs a data source regardless of Hibernate); if it fails,
     * nothing is left open and {@link #entityManagerFactory} is never assigned, so a bad migration
     * or a briefly unreachable Postgres does not leak a connection pool on every retry.
     */
    private EntityManagerFactory entityManagerFactory() {
        var existing = entityManagerFactory;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (entityManagerFactory == null) {
                var config = configSupplier.get();
                migrate(config);
                entityManagerFactory = createEntityManagerFactory(config);
            }
            return entityManagerFactory;
        }
    }

    private static void migrate(DatabaseConfig config) {
        try {
            Flyway.configure()
                    .dataSource(config.jdbcUrl(), config.user(), config.password())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
        } catch (RuntimeException e) {
            throw new ChallengeProgressPersistenceException(
                    "Failed to migrate the challenge progress schema", e);
        }
    }

    /**
     * Builds the entity manager factory through the standard
     * {@link Persistence#createEntityManagerFactory(String, Map)} entry point, overriding
     * {@code persistence.xml} with the connection settings from {@code config}: Hibernate's own
     * HikariCP connection provider owns the pool ({@code hibernate.connection.provider_class}), fed
     * with the Hikari specific {@code hibernate.hikari.*} properties. These values are only known at
     * runtime (read from environment variables via {@link DatabaseConfig}), so they cannot live in
     * the static {@code persistence.xml} file; {@code hibernate.hbm2ddl.auto=validate} does live in
     * {@code persistence.xml} since it never depends on the environment.
     */
    private static EntityManagerFactory createEntityManagerFactory(DatabaseConfig config) {
        var overrides = Map.<String, Object>of(
                "hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider",
                "hibernate.hikari.jdbcUrl", config.jdbcUrl(),
                "hibernate.hikari.dataSource.user", config.user(),
                "hibernate.hikari.dataSource.password", config.password(),
                "hibernate.hikari.poolName", "skygridng-challenge-progress");
        try {
            return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, overrides);
        } catch (RuntimeException e) {
            throw new ChallengeProgressPersistenceException(
                    "Failed to build the challenge progress entity manager factory", e);
        }
    }

    @Override
    public int tierOf(UUID playerId, String challengeId) {
        try (var entityManager = entityManagerFactory().createEntityManager()) {
            var entity = entityManager.find(ChallengeTierEntity.class, new ChallengeTierId(playerId, challengeId));
            return entity == null ? 0 : entity.getTier();
        } catch (RuntimeException e) {
            throw new ChallengeProgressPersistenceException(
                    "Failed to read the challenge tier for player " + playerId + ", challenge " + challengeId, e);
        }
    }

    /**
     * Records a completion inside a transaction, locating the row with
     * {@link LockModeType#PESSIMISTIC_WRITE} and either persisting a new row at tier 1 or
     * incrementing the managed entity's tier field (flushed on commit, no explicit {@code merge}
     * needed).
     *
     * <p>This is a deliberate behavior change from the previous hand-rolled JDBC adapter, which used
     * a single atomic {@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING} statement: Jakarta
     * Persistence has no portable upsert operation, so two concurrent completions for the exact same
     * player and challenge, once a row already exists, are now serialized by the pessimistic lock
     * (the second transaction blocks until the first commits, then reads the already-incremented
     * row) rather than the database resolving the conflict atomically in one round trip.
     *
     * <p>A pessimistic lock on {@link EntityManager#find} cannot lock a row that does not exist yet,
     * so it gives no protection on its own for the very first completion of a given player and
     * challenge: two concurrent inserts for the same {@link ChallengeTierId} would otherwise race the
     * table's primary key and one of them would fail with a constraint violation instead of applying.
     * {@link #recordCompletionAttempt} handles that case explicitly: an insert that loses this race
     * surfaces as a {@link ConstraintViolationException} once the other transaction commits (Postgres
     * blocks the second insert on the primary key entry until the first transaction resolves, then
     * reports the duplicate), which is treated as proof the row now exists and retried exactly once as
     * a plain locked increment. Both completions still apply and no update is lost, but the second
     * call now waits on either a row lock or a primary key conflict instead of racing a single
     * statement.
     */
    @Override
    public int recordCompletion(UUID playerId, String challengeId) {
        var id = new ChallengeTierId(playerId, challengeId);
        try {
            return recordCompletionAttempt(id, true);
        } catch (RuntimeException e) {
            throw new ChallengeProgressPersistenceException(
                    "Failed to record a challenge completion for player " + playerId + ", challenge " + challengeId, e);
        }
    }

    /**
     * Runs one attempt at recording a completion, in its own {@link EntityManager} and its own
     * transaction. A retry always opens a brand new {@link EntityManager} rather than reusing the one
     * whose transaction just rolled back: after a rollback the persistence context can be left with
     * the entity this attempt tried to insert in an inconsistent, no-longer-managed state, so reusing
     * it risks a stale {@code find} instead of a real read of the row the other transaction committed.
     *
     * <p>{@code allowInsertRetry} bounds the retry to exactly one: only a fresh insert (the
     * {@code entity == null} branch) can lose the primary key race described on
     * {@link #recordCompletion}, and once that race is lost the row is guaranteed to exist (the
     * conflicting insert only fails after the other transaction that created it has committed), so a
     * single retry as a locked increment is always enough; it is never re-armed on the retried call,
     * which prevents any risk of unbounded recursion.
     */
    private int recordCompletionAttempt(ChallengeTierId id, boolean allowInsertRetry) {
        try (var entityManager = entityManagerFactory().createEntityManager()) {
            var transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                var entity = entityManager.find(ChallengeTierEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
                int newTier;
                if (entity == null) {
                    entityManager.persist(new ChallengeTierEntity(id, 1));
                    entityManager.flush();
                    newTier = 1;
                } else {
                    entity.incrementTier();
                    newTier = entity.getTier();
                }
                transaction.commit();
                return newTier;
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                if (allowInsertRetry && isUniqueConstraintViolation(e)) {
                    return recordCompletionAttempt(id, false);
                }
                throw e;
            }
        }
    }

    /**
     * Walks {@code exception}'s cause chain looking for a {@link ConstraintViolationException}, the
     * Hibernate-specific signal (already relied on elsewhere in this class, alongside the
     * HikariCP-specific connection provider) that an insert failed on the table's primary key rather
     * than on some other, unrelated failure that should simply propagate.
     */
    private static boolean isUniqueConstraintViolation(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    /**
     * The season-reset operation, built as a {@link CriteriaDelete} over Hibernate's generated
     * static metamodel ({@code ChallengeTierEntity_}), the one place in this repository where the
     * metamodel generator earns its keep: every other query here is a primary-key {@code find}, for
     * which Criteria would only add ceremony. The {@code tier >= 0} predicate is not a real filter
     * (the {@code tier} column has a {@code CHECK (tier >= 0)} constraint, so it always matches every
     * row); it exists so this bulk delete goes through a type-safe attribute reference
     * ({@code ChallengeTierEntity_.tier}) instead of an unconditional statement with no Criteria
     * attribute access at all.
     */
    @Override
    public void resetAll() {
        try (var entityManager = entityManagerFactory().createEntityManager()) {
            var transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                var builder = entityManager.getCriteriaBuilder();
                CriteriaDelete<ChallengeTierEntity> delete = builder.createCriteriaDelete(ChallengeTierEntity.class);
                var root = delete.from(ChallengeTierEntity.class);
                delete.where(builder.ge(root.get(ChallengeTierEntity_.tier), 0));
                entityManager.createQuery(delete).executeUpdate();
                transaction.commit();
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        } catch (RuntimeException e) {
            throw new ChallengeProgressPersistenceException("Failed to reset all challenge progress", e);
        }
    }

    /**
     * Closes the entity manager factory (and with it, Hibernate's HikariCP pool), if one was ever
     * built. Call once, on plugin disable. Safe to call even if this repository's methods were never
     * invoked (the factory was never initialized), in which case this is a no-op rather than a null
     * pointer exception.
     */
    @Override
    public void close() {
        var existing = entityManagerFactory;
        if (existing != null) {
            existing.close();
        }
    }
}
