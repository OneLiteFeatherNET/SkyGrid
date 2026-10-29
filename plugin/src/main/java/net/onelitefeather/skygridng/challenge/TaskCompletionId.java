package net.onelitefeather.skygridng.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import net.onelitefeather.skygridng.season.SeasonId;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.UUID;

/**
 * The composite primary key of {@link TaskCompletionEntity}: one row per (season, player, challenge,
 * prestige step, task) tuple, matching the primary key of the {@code task_completion} table after
 * {@code V2__scope_completion_by_season_and_step.sql}.
 *
 * @param seasonId     the season of the completion, column {@code season_id}
 * @param playerId     the player's UUID, column {@code player_id}
 * @param challengeId  the challenge's id, column {@code challenge_id}
 * @param prestigeStep the prestige step, column {@code prestige_step} (SMALLINT, 0 to {@link ProgressScope#MAX_PRESTIGE_STEP})
 * @param taskId       the task's id, column {@code task_id}
 */
@Embeddable
public record TaskCompletionId(
        @Column(name = "season_id", nullable = false) SeasonId seasonId,
        @Column(name = "player_id", nullable = false) UUID playerId,
        @Column(name = "challenge_id", nullable = false, length = ChallengeRule.MAX_ID_LENGTH) String challengeId,
        @Column(name = "prestige_step", nullable = false) @JdbcTypeCode(SqlTypes.SMALLINT) int prestigeStep,
        @Column(name = "task_id", nullable = false, length = Task.MAX_ID_LENGTH) String taskId)
        implements Serializable {

    public TaskCompletionId {
        if (seasonId == null) {
            throw new IllegalArgumentException("TaskCompletionId.seasonId must not be null");
        }
        if (playerId == null) {
            throw new IllegalArgumentException("TaskCompletionId.playerId must not be null");
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("TaskCompletionId.challengeId must not be blank");
        }
        if (challengeId.length() > ChallengeRule.MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "TaskCompletionId.challengeId must be at most " + ChallengeRule.MAX_ID_LENGTH + " characters");
        }
        if (prestigeStep < 0 || prestigeStep > ProgressScope.MAX_PRESTIGE_STEP) {
            throw new IllegalArgumentException(
                    "TaskCompletionId.prestigeStep must be between 0 and " + ProgressScope.MAX_PRESTIGE_STEP);
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskCompletionId.taskId must not be blank");
        }
        if (taskId.length() > Task.MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "TaskCompletionId.taskId must be at most " + Task.MAX_ID_LENGTH + " characters");
        }
    }
}
