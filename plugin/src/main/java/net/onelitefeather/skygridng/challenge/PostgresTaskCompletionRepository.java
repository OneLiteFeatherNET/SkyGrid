package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.adapters.persistence.Database;
import net.onelitefeather.skygridng.api.model.SeasonId;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PostgreSQL-backed {@link TaskCompletionRepository}, persisted through Jakarta Persistence (JSR 338) via
 * Hibernate ORM on the shared {@link Database}. Schema ownership: the shared migrations in
 * {@code classpath:db/migration} (V4 and V5 for this table).
 *
 * <p>Construction is cheap and side effect free: the database is bootstrapped lazily, per {@link Database}.
 */
public final class PostgresTaskCompletionRepository implements TaskCompletionRepository {

    private final Database database;

    /**
     * @param database the shared database this repository queries.
     */
    public PostgresTaskCompletionRepository(Database database) {
        this.database = database;
    }

    private static TaskCompletionId idOf(ProgressScope scope, String taskId) {
        return new TaskCompletionId(
                scope.season(), scope.playerId(), scope.challengeId(), scope.prestigeStep(), taskId);
    }

    @Override
    public boolean isCompleted(ProgressScope scope, String taskId) {
        var id = idOf(scope, taskId);
        return database.read(entityManager -> entityManager.find(TaskCompletionEntity.class, id) != null);
    }

    /**
     * Records completion inside a transaction, if not already recorded. Two concurrent first completions of
     * the same brand new tuple can race the primary key; the loser's {@link ConstraintViolationException} is
     * retried exactly once as an existence check, returning {@code false}.
     */
    @Override
    public boolean markCompleted(ProgressScope scope, String taskId) {
        return markCompletedAttempt(idOf(scope, taskId), true);
    }

    private boolean markCompletedAttempt(TaskCompletionId id, boolean allowInsertRetry) {
        try {
            return database.inTransaction(entityManager -> {
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

    /** Walks the cause chain for a Hibernate {@link ConstraintViolationException} (a primary key conflict). */
    private static boolean isUniqueConstraintViolation(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    /** Named bulk delete of one season's completions; every other season is untouched. */
    @Override
    public void resetSeason(SeasonId season) {
        database.inTransaction(entityManager -> entityManager
                .createNamedQuery("TaskCompletionEntity.deleteBySeason")
                .setParameter("seasonId", season)
                .executeUpdate());
    }

    /** Counts the player's completions per season, read through the {@code ofPlayer} named query. */
    @Override
    public Map<SeasonId, Long> completedCountBySeason(UUID playerId) {
        return database.read(entityManager -> entityManager
                .createNamedQuery("TaskCompletionEntity.ofPlayer", TaskCompletionEntity.class)
                .setParameter("playerId", playerId)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(entity -> entity.getId().seasonId(), Collectors.counting())));
    }
}
