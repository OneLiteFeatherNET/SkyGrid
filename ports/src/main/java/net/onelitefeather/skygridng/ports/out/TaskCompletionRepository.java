package net.onelitefeather.skygridng.ports.out;

import java.util.UUID;

/**
 * Out port: where the core reads and writes per-player task completion.
 *
 * One row per (player, challenge, task) triple. Deliberately holds no reward logic: {@link
 * #markCompleted} is a pure idempotent persistence operation, the deciding whether to grant a
 * reward stays in {@code core.service.TaskProgressService}. Expected to be database backed (see
 * {@link ChallengeProgressRepository} for the precedent), but the core does not know or care which.
 */
public interface TaskCompletionRepository {

    /** Whether {@code playerId} has completed {@code taskId} within {@code challengeId}. */
    boolean isCompleted(UUID playerId, String challengeId, String taskId);

    /**
     * Records completion of {@code taskId} within {@code challengeId} by {@code playerId} if not
     * already recorded. A pure idempotent no-op: returns {@code false} and writes nothing if the row
     * already existed, {@code true} if it just inserted a new completion row.
     */
    boolean markCompleted(UUID playerId, String challengeId, String taskId);

    /** The season-reset operation: clears every recorded task completion for every player. */
    void resetAll();
}
