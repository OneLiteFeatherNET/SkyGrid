package net.onelitefeather.skygridng.adapters.challengeprogress;

import jakarta.persistence.LockModeType;
import net.onelitefeather.skygridng.adapters.persistence.AbstractJpaRepository;
import net.onelitefeather.skygridng.adapters.persistence.DatabaseConfig;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * PostgreSQL-backed {@link ChallengeProgressRepository}, persisted through Jakarta Persistence (JSR 338)
 * via Hibernate ORM. The bootstrap, Flyway migration and transaction plumbing is inherited from
 * {@link AbstractJpaRepository}; this class adds the season scoped queries of the {@code challenge_tier}
 * table. Schema ownership: {@code classpath:db/migration/challenge-progress} (V1 to V3).
 *
 * <p>Construction is cheap and side effect free, per {@link AbstractJpaRepository}'s contract.
 */
public final class PostgresChallengeProgressRepository extends AbstractJpaRepository
        implements ChallengeProgressRepository {

    /**
     * @param configSupplier supplies the connection settings on first use, normally
     *                       {@code DatabaseConfig::fromEnvironment}.
     */
    public PostgresChallengeProgressRepository(Supplier<DatabaseConfig> configSupplier) {
        super(
                "challenge-progress",
                "classpath:db/migration/challenge-progress",
                "flyway_history_challenge_progress",
                configSupplier);
    }

    @Override
    public int tierOf(SeasonId season, UUID playerId, String challengeId) {
        return read(entityManager -> {
            var entity = entityManager.find(ChallengeTierEntity.class, new ChallengeTierId(season, playerId, challengeId));
            return entity == null ? 0 : entity.getTier();
        });
    }

    /**
     * Records a completion inside a transaction, locating the row with {@link LockModeType#PESSIMISTIC_WRITE}
     * and either persisting a new row at tier 1 or incrementing the managed entity's tier (flushed on commit).
     *
     * <p>A pessimistic lock cannot lock a row that does not exist yet, so two concurrent first completions
     * of the same (season, player, challenge) can race the primary key. {@link #recordCompletionAttempt}
     * handles that: an insert that loses the race surfaces as a {@link ConstraintViolationException}, and is
     * retried exactly once as a plain locked increment.
     */
    @Override
    public int recordCompletion(SeasonId season, UUID playerId, String challengeId) {
        return recordCompletionAttempt(new ChallengeTierId(season, playerId, challengeId), true);
    }

    /**
     * Runs one attempt at recording a completion, in its own transaction. A retry opens a fresh
     * {@code EntityManager}; {@code allowInsertRetry} bounds the retry to exactly one.
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

    /** Walks the cause chain for a Hibernate {@link ConstraintViolationException} (a primary key conflict). */
    private static boolean isUniqueConstraintViolation(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    /** Named bulk delete of one season's tiers; every other season is untouched. */
    @Override
    public void resetSeason(SeasonId season) {
        inTransaction(entityManager -> entityManager
                .createNamedQuery("ChallengeTierEntity.deleteBySeason")
                .setParameter("seasonId", season)
                .executeUpdate());
    }

    /** The player's tier per season, read through the {@code historyOf} named query. */
    @Override
    public Map<SeasonId, Integer> history(UUID playerId) {
        return read(entityManager -> entityManager
                .createNamedQuery("ChallengeTierEntity.historyOf", ChallengeTierEntity.class)
                .setParameter("playerId", playerId)
                .getResultList()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        entity -> entity.getId().seasonId(),
                        ChallengeTierEntity::getTier)));
    }
}
