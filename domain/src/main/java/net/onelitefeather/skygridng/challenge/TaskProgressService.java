package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.ports.in.CurrentSeason;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implements {@link TaskProgressUseCase}: recording per-player task completion and granting a
 * task's reward exactly once.
 *
 * Reads the configured task list and {@code sequential} flag from {@link ChallengeState} rather
 * than a {@code ChallengeRepository} round trip on every call, the same tradeoff {@link
 * ChallengeAdminService} makes: {@code ChallengeState} is already the project's in-memory cache of
 * the live {@code ChallengeCatalog} (kept current by every admin change), so a task lookup is a
 * lock-free read instead of disk I/O on the game-logic hot path. Unlike {@link
 * ChallengeProgressService}, this class does carry logic beyond a pure delegate: the sequential
 * ordering check and the "grant once, on first completion" rule both live here, never in the
 * repository.
 */
public final class TaskProgressService implements TaskProgressUseCase {

    private final ChallengeState challengeState;
    private final TaskCompletionRepository repository;
    private final RewardGrantor grantor;
    private final CurrentSeason currentSeason;
    private final List<TaskCompletionListener> listeners = new CopyOnWriteArrayList<>();

    public TaskProgressService(ChallengeState challengeState, TaskCompletionRepository repository,
                               RewardGrantor grantor, CurrentSeason currentSeason) {
        this.challengeState = challengeState;
        this.repository = repository;
        this.grantor = grantor;
        this.currentSeason = currentSeason;
    }

    /** The scope of a completion by {@code playerId}: the current season, prestige step 0 until the prestige change. */
    private ProgressScope scopeOf(UUID playerId, String challengeId) {
        return new ProgressScope(currentSeason.id(), playerId, challengeId, 0);
    }

    @Override
    public boolean isCompleted(UUID playerId, String challengeId, String taskId) {
        return repository.isCompleted(scopeOf(playerId, challengeId), taskId);
    }

    @Override
    public List<TaskStatus> tasksFor(UUID playerId, String challengeId) {
        var rule = challengeState.current().forId(challengeId);
        if (rule.isEmpty()) {
            return List.of();
        }
        return rule.get().tasks().stream()
            .map(task -> new TaskStatus(task, repository.isCompleted(scopeOf(playerId, challengeId), task.id())))
            .toList();
    }

    @Override
    public boolean completeTask(UUID playerId, String challengeId, String taskId) {
        var maybeRule = challengeState.current().forId(challengeId);
        if (maybeRule.isEmpty()) {
            return false;
        }
        ChallengeRule rule = maybeRule.get();
        List<Task> tasks = rule.tasks();
        int index = indexOf(tasks, taskId);
        if (index < 0) {
            return false;
        }

        if (rule.sequential()) {
            for (int i = 0; i < index; i++) {
                if (!repository.isCompleted(scopeOf(playerId, challengeId), tasks.get(i).id())) {
                    return false;
                }
            }
        }

        if (!repository.markCompleted(scopeOf(playerId, challengeId), taskId)) {
            return false;
        }

        Task task = tasks.get(index);
        if (task.hasReward()) {
            grantor.grant(playerId, task.reward());
        }
        fireCompleted(playerId, challengeId, taskId);
        return true;
    }

    @Override
    public void addCompletionListener(TaskCompletionListener listener) {
        listeners.add(listener);
    }

    private static int indexOf(List<Task> tasks, String taskId) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    private void fireCompleted(UUID playerId, String challengeId, String taskId) {
        for (TaskCompletionListener listener : listeners) {
            listener.onTaskCompleted(playerId, challengeId, taskId);
        }
    }
}
