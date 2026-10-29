package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.Task;

import java.util.List;
import java.util.UUID;

/**
 * In port: reading and recording per-player task completion within a {@link
 * net.onelitefeather.skygridng.api.model.ChallengeRule}'s quest (called by the driving adapter that
 * detects a task's completion trigger, for example a {@code ChallengeBehavior} listener).
 */
public interface TaskProgressUseCase {

    /** Whether {@code playerId} has completed {@code taskId} within {@code challengeId}. */
    boolean isCompleted(UUID playerId, String challengeId, String taskId);

    /**
     * The scoreboard read: {@code challengeId}'s tasks in their configured order, each paired with
     * whether {@code playerId} has completed it. Empty for an unknown challenge id.
     */
    List<TaskStatus> tasksFor(UUID playerId, String challengeId);

    /**
     * Records completion of {@code taskId} within {@code challengeId} by {@code playerId}, and
     * grants the task's reward if any. Idempotent and never throws:
     *
     * <ul>
     *   <li>Returns {@code true} only when this call actually recorded a new completion (and
     *       granted the reward, if the task has one).</li>
     *   <li>Returns {@code false} as a no-op when the task was already completed (no duplicate
     *       reward).</li>
     *   <li>Returns {@code false} as a no-op when the challenge is sequential and an earlier task
     *       in its configured order is not yet completed for this player. This is not an error; the
     *       caller is expected to call again later from its own game-logic trigger.</li>
     *   <li>Returns {@code false} as a no-op for an unknown challenge id or a task id not part of
     *       the challenge.</li>
     * </ul>
     */
    boolean completeTask(UUID playerId, String challengeId, String taskId);

    /**
     * Registers a listener invoked every time {@link #completeTask} actually records a new
     * completion. Carries just the changed (player, challenge, task) triple, so a scoreboard adapter
     * can update the one affected line instead of re-rendering the whole board.
     */
    void addCompletionListener(TaskCompletionListener listener);

    /** One task paired with whether {@code playerId} has completed it, for the scoreboard read. */
    record TaskStatus(Task task, boolean done) {
    }

    /**
     * Notified with the identifying triple of a task completion that {@link #completeTask} just
     * recorded. Not invoked for a no-op call (already completed, sequential rejection, or unknown
     * id).
     */
    @FunctionalInterface
    interface TaskCompletionListener {
        void onTaskCompleted(UUID playerId, String challengeId, String taskId);
    }
}
