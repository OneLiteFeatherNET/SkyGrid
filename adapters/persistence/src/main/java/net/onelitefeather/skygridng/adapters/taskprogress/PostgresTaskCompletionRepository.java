package net.onelitefeather.skygridng.adapters.taskprogress;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaDelete;
import net.onelitefeather.skygridng.adapters.persistence.AbstractJpaRepository;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.ports.out.TaskCompletionRepository;
import org.hibernate.exception.ConstraintViolationException;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * PostgreSQL-backed {@link TaskCompletionRepository}, persisted through Jakarta Persistence
 * (JSR 338) via Hibernate ORM. The factory-bootstrap and transaction-lifecycle plumbing (lazy
 * {@link jakarta.persistence.EntityManagerFactory} construction, Flyway migration, transaction
 * begin/commit/rollback) is inherited from {@link AbstractJpaRepository}; this class only adds the
 * behavior specific to the {@code task_completion} table.
 *
 * <p>The schema is owned by the Flyway migration bundled at
 * {@code classpath:db/migration/task-progress/V1__create_task_completion.sql}, never a hand-edited
 * {@code CREATE TABLE} assumed to match this class; Hibernate never creates or alters that schema
 * itself ({@code hibernate.hbm2ddl.auto=validate} in {@code persistence.xml}), it only validates the
 * {@link TaskCompletionEntity} mapping against the table Flyway already created.
 *
 * <p>Construction is cheap and side effect free, per {@link AbstractJpaRepository}'s contract:
 * nothing is resolved, connected, or migrated until the first call to {@link #isCompleted},
 * {@link #markCompleted}, or {@link #resetAll}.
 */
public final class PostgresTaskCompletionRepository extends AbstractJpaRepository
        implements TaskCompletionRepository {

    /**
     * @param configSupplier supplies the connection settings on first use, normally
     *                       {@code DatabaseConfig::fromEnvironment}.
     */
    public PostgresTaskCompletionRepository(Supplier<DatabaseConfig> configSupplier) {
        super(
                "task-progress",
                "classpath:db/migration/task-progress",
                "flyway_history_task_progress",
                configSupplier);
    }

    @Override
    public boolean isCompleted(UUID playerId, String challengeId, String taskId) {
        var id = new TaskCompletionId(playerId, challengeId, taskId);
        return read(entityManager -> entityManager.find(TaskCompletionEntity.class, id) != null);
    }

    /**
     * Records completion inside a transaction, if not already recorded. Unlike
     * {@code PostgresChallengeProgressRepository.recordCompletion}, there is nothing to mutate once a
     * row is found (row existence alone is the completion fact, no counter or status field), so the
     * "already exists" path needs no pessimistic lock: a plain {@link EntityManager#find} is enough
     * to decide "nothing to do".
     *
     * <p>The only race that matters is two concurrent completions of the exact same, brand-new
     * (player, challenge, task) triple: {@link EntityManager#find} cannot lock a row that does not
     * exist yet, so both transactions can observe {@code entity == null} and both attempt to
     * {@code persist}. Postgres serializes the two inserts on the primary key; the loser surfaces a
     * {@link ConstraintViolationException} once the winner commits, which {@link #markCompletedAttempt}
     * treats as proof the row now exists and retries exactly once as a plain existence check,
     * returning {@code false} without throwing.
     */
    @Override
    public boolean markCompleted(UUID playerId, String challengeId, String taskId) {
        var id = new TaskCompletionId(playerId, challengeId, taskId);
        return markCompletedAttempt(id, true);
    }

    /**
     * Runs one attempt at recording completion, in its own transaction via {@link #inTransaction}. A
     * retry always opens a brand new {@link EntityManager} (a fresh call to {@link #inTransaction})
     * rather than reusing the one whose transaction just rolled back: after a rollback the
     * persistence context can be left with the entity this attempt tried to insert in an
     * inconsistent, no-longer-managed state, so reusing it risks a stale {@code find} instead of a
     * real read of the row the other transaction committed.
     *
     * <p>{@code allowInsertRetry} bounds the retry to exactly one: only a fresh insert (the
     * {@code entity == null} branch) can lose the primary key race, and once that race is lost the
     * row is guaranteed to exist (the conflicting insert only fails after the other transaction that
     * created it has committed), so a single retry as a locked-free existence check is always enough;
     * it is never re-armed on the retried call, which prevents any risk of unbounded recursion.
     */
    private boolean markCompletedAttempt(TaskCompletionId id, boolean allowInsertRetry) {
        try {
            return inTransaction(entityManager -> {
                var entity = entityManager.find(TaskCompletionEntity.class, id);
                if (entity != null) {
                    return false;
                }
                entityManager.persist(new TaskCompletionEntity(id));
                entityManager.flush();
                return true;
            });
        } catch (RuntimeException e) {
            if (allowInsertRetry && isUniqueConstraintViolation(e)) {
                return markCompletedAttempt(id, false);
            }
            throw e;
        }
    }

    /**
     * Walks {@code exception}'s cause chain looking for a {@link ConstraintViolationException}, the
     * Hibernate-specific signal that an insert failed on the table's primary key rather than on some
     * other, unrelated failure that should simply propagate.
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
     * static metamodel ({@code TaskCompletionEntity_}), the one place in this repository where the
     * metamodel generator earns its keep: every other query here is a primary-key {@code find}, for
     * which Criteria would only add ceremony. The {@code id is not null} predicate is not a real
     * filter (the embedded id is always present on a persisted row); it exists so this bulk delete
     * goes through a type-safe attribute reference ({@code TaskCompletionEntity_.id}) instead of an
     * unconditional statement with no Criteria attribute access at all.
     */
    @Override
    public void resetAll() {
        inTransaction(entityManager -> {
            var builder = entityManager.getCriteriaBuilder();
            CriteriaDelete<TaskCompletionEntity> delete = builder.createCriteriaDelete(TaskCompletionEntity.class);
            var root = delete.from(TaskCompletionEntity.class);
            delete.where(builder.isNotNull(root.get(TaskCompletionEntity_.id)));
            return entityManager.createQuery(delete).executeUpdate();
        });
    }
}
