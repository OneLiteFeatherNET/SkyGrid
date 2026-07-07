package net.onelitefeather.skygridng.adapters.taskprogress;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.Task;

import java.io.Serializable;
import java.util.UUID;

/**
 * The composite primary key of {@link TaskCompletionEntity}: one row per (player, challenge, task)
 * triple, matching the {@code PRIMARY KEY (player_id, challenge_id, task_id)} of the
 * {@code task_completion} table owned by {@code V1__create_task_completion.sql}.
 *
 * <p>A record is a valid JPA embeddable id here (Hibernate 6.2+ supports records as embeddable
 * types), unlike {@link TaskCompletionEntity} itself: an id class only needs value semantics
 * (immutability, and {@code equals}/{@code hashCode} over every attribute, both of which a record
 * gives for free) and {@link Serializable}, it does not need the mutable, proxyable shape that an
 * owning entity needs for Hibernate's dirty-checking machinery.
 *
 * @param playerId    the player's UUID, column {@code player_id}
 * @param challengeId the challenge's id, column {@code challenge_id}
 * @param taskId      the task's id, column {@code task_id}
 */
@Embeddable
public record TaskCompletionId(
        @Column(name = "player_id", nullable = false) UUID playerId,
        @Column(name = "challenge_id", nullable = false, length = ChallengeRule.MAX_ID_LENGTH) String challengeId,
        @Column(name = "task_id", nullable = false, length = Task.MAX_ID_LENGTH) String taskId)
        implements Serializable {

    public TaskCompletionId {
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
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskCompletionId.taskId must not be blank");
        }
        if (taskId.length() > Task.MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "TaskCompletionId.taskId must be at most " + Task.MAX_ID_LENGTH + " characters");
        }
    }
}
