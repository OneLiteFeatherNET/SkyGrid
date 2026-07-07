package net.onelitefeather.skygridng.adapters.challengeprogress;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaDelete;
import net.onelitefeather.skygridng.adapters.persistence.AbstractJpaRepository;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import org.hibernate.exception.ConstraintViolationException;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * PostgreSQL-backed {@link ChallengeProgressRepository}, persisted through Jakarta Persistence
 * (JSR 338) via Hibernate ORM. The factory-bootstrap and transaction-lifecycle plumbing (lazy
 * {@link jakarta.persistence.EntityManagerFactory} construction, Flyway migration, transaction
 * begin/commit/rollback) is inherited from {@link AbstractJpaRepository}; this class only adds the
 * behavior specific to the {@code challenge_tier} table.
 *
 * <p>The schema is owned by the Flyway migration bundled at
 * {@code classpath:db/migration/V1__create_challenge_tier.sql}, never a hand-edited
 * {@code CREATE TABLE} assumed to match this class; Hibernate never creates or alters that schema
 * itself ({@code hibernate.hbm2ddl.auto=validate} in {@code persistence.xml}), it only validates the
 * {@link ChallengeTierEntity} mapping against the table Flyway already created.
 *
 * <p>Construction is cheap and side effect free, per {@link AbstractJpaRepository}'s contract:
 * nothing is resolved, connected, or migrated until the first call to {@link #tierOf},
 * {@link #recordCompletion}, or {@link #resetAll}.
 */
public final class PostgresChallengeProgressRepository extends AbstractJpaRepository
        implements ChallengeProgressRepository {

    /**
     * @param configSupplier supplies the connection settings on first use, normally
     *                       {@code DatabaseConfig::fromEnvironment}.
     */
    public PostgresChallengeProgressRepository(Supplier<DatabaseConfig> configSupplier) {
        super("challenge-progress", "classpath:db/migration", configSupplier);
    }

    @Override
    public int tierOf(UUID playerId, String challengeId) {
        return read(entityManager -> {
            var entity = entityManager.find(ChallengeTierEntity.class, new ChallengeTierId(playerId, challengeId));
            return entity == null ? 0 : entity.getTier();
        });
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
        return recordCompletionAttempt(id, true);
    }

    /**
     * Runs one attempt at recording a completion, in its own transaction via {@link #inTransaction}.
     * A retry always opens a brand new {@link EntityManager} (a fresh call to {@link #inTransaction})
     * rather than reusing the one whose transaction just rolled back: after a rollback the
     * persistence context can be left with the entity this attempt tried to insert in an
     * inconsistent, no-longer-managed state, so reusing it risks a stale {@code find} instead of a
     * real read of the row the other transaction committed.
     *
     * <p>{@code allowInsertRetry} bounds the retry to exactly one: only a fresh insert (the
     * {@code entity == null} branch) can lose the primary key race described on
     * {@link #recordCompletion}, and once that race is lost the row is guaranteed to exist (the
     * conflicting insert only fails after the other transaction that created it has committed), so a
     * single retry as a locked increment is always enough; it is never re-armed on the retried call,
     * which prevents any risk of unbounded recursion.
     */
    private int recordCompletionAttempt(ChallengeTierId id, boolean allowInsertRetry) {
        try {
            return inTransaction(entityManager -> {
                var entity = entityManager.find(ChallengeTierEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
                if (entity == null) {
                    entityManager.persist(new ChallengeTierEntity(id, 1));
                    entityManager.flush();
                    return 1;
                }
                entity.incrementTier();
                return entity.getTier();
            });
        } catch (RuntimeException e) {
            if (allowInsertRetry && isUniqueConstraintViolation(e)) {
                return recordCompletionAttempt(id, false);
            }
            throw e;
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
        inTransaction(entityManager -> {
            var builder = entityManager.getCriteriaBuilder();
            CriteriaDelete<ChallengeTierEntity> delete = builder.createCriteriaDelete(ChallengeTierEntity.class);
            var root = delete.from(ChallengeTierEntity.class);
            delete.where(builder.ge(root.get(ChallengeTierEntity_.tier), 0));
            return entityManager.createQuery(delete).executeUpdate();
        });
    }
}
