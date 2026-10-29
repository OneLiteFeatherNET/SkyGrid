package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.season.SeasonId;

import java.util.Map;
import java.util.UUID;

/**
 * Out port: where the core reads and writes per-player task completion, scoped by season and prestige step.
 *
 * One row per (season, player, challenge, prestige step, task) tuple. Deliberately holds no reward logic:
 * {@link #markCompleted} is a pure idempotent persistence operation, the deciding whether to grant a
 * reward stays in {@code core.service.TaskProgressService}. Expected to be database backed (see
 * {@link ChallengeProgressRepository} for the precedent), but the core does not know or care which.
 */
public interface TaskCompletionRepository {

    /** Whether the scoped player has completed {@code taskId} within the scope's challenge. */
    boolean isCompleted(ProgressScope scope, String taskId);

    /**
     * Records completion of {@code taskId} within the scope's challenge if not already recorded. A pure
     * idempotent no-op: returns {@code false} and writes nothing if the row already existed, {@code true}
     * if it just inserted a new completion row.
     */
    boolean markCompleted(ProgressScope scope, String taskId);

    /** The season-reset operation: clears every task completion of {@code season} only. */
    void resetSeason(SeasonId season);

    /** The number of completed tasks of {@code playerId} per season. */
    Map<SeasonId, Long> completedCountBySeason(UUID playerId);
}
