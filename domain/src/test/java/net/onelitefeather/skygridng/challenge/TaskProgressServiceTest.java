package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.challenge.TaskProgressUseCase.TaskCompletionListener;
import net.onelitefeather.skygridng.challenge.TaskProgressUseCase.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Black-box tests for {@link TaskProgressService} against a real in memory {@link ChallengeState}
 * (mirrors {@link ChallengeAdminServiceTest}'s approach), a Mockito fake for {@link RewardGrantor}, an
 * in memory {@link TaskCompletionRepository} fake, and a switchable {@link CurrentSeason}.
 */
class TaskProgressServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final SeasonId SEASON_ONE = new SeasonId("s01");
    private static final SeasonId SEASON_TWO = new SeasonId("s02");

    private static final String SEQUENTIAL_CHALLENGE = "sequential_quest";
    private static final Task TASK_A = new Task("task_a", "Do a", new Reward.ItemGrant(ItemId.of("minecraft:diamond"), 1));
    private static final Task TASK_B = new Task("task_b", "Do b", new Reward.PlayerMessage("<green>Well done!</green>"));

    private static final String INDEPENDENT_CHALLENGE = "independent_quest";
    private static final Task TASK_C = new Task("task_c", "Do c", null);
    private static final Task TASK_D = new Task("task_d", "Do d", null);

    private FakeTaskCompletionRepository repository;
    private RewardGrantor grantor;
    private SwitchableSeason currentSeason;
    private TaskProgressUseCase progress;

    @BeforeEach
    void setUp() {
        ChallengeCatalog catalog = new ChallengeCatalog(Map.of(
                SEQUENTIAL_CHALLENGE, new ChallengeRule(SEQUENTIAL_CHALLENGE, "sequential", true, List.of(TASK_A, TASK_B), true),
                INDEPENDENT_CHALLENGE, new ChallengeRule(INDEPENDENT_CHALLENGE, "independent", true, List.of(TASK_C, TASK_D), false)));
        ChallengeState state = new ChallengeState(catalog);
        repository = new FakeTaskCompletionRepository();
        grantor = mock(RewardGrantor.class);
        currentSeason = new SwitchableSeason(SEASON_ONE);
        progress = new TaskProgressService(state, repository, grantor, currentSeason);
    }

    @Test
    @DisplayName("completing a task grants its reward exactly once")
    void completingTaskGrantsRewardOnce() {
        boolean result = progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id());

        assertThat(result).isTrue();
        assertThat(progress.isCompleted(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id())).isTrue();
        verify(grantor, times(1)).grant(PLAYER_ID, TASK_A.reward());
    }

    @Test
    @DisplayName("a second completion of an already completed task is an idempotent no-op")
    void secondCompletionIsNoOp() {
        TaskCompletionListener listener = mock(TaskCompletionListener.class);
        progress.addCompletionListener(listener);

        boolean first = progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id());
        boolean second = progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id());

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(grantor, times(1)).grant(PLAYER_ID, TASK_A.reward());
        verify(listener, times(1)).onTaskCompleted(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id());
    }

    @Test
    @DisplayName("sequential challenge rejects completing a later task before an earlier one")
    void sequentialRejectsOutOfOrder() {
        boolean result = progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_B.id());

        assertThat(result).isFalse();
        assertThat(repository.markCompletedCalls).isEmpty();
        assertThat(progress.isCompleted(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_B.id())).isFalse();
        verify(grantor, never()).grant(PLAYER_ID, TASK_B.reward());
    }

    @Test
    @DisplayName("sequential challenge accepts tasks completed in configured order")
    void sequentialAcceptsInOrder() {
        boolean firstResult = progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id());
        boolean secondResult = progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_B.id());

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isTrue();
        verify(grantor).grant(PLAYER_ID, TASK_A.reward());
        verify(grantor).grant(PLAYER_ID, TASK_B.reward());
    }

    @Test
    @DisplayName("independent (non-sequential) challenge accepts tasks completed out of order")
    void independentAcceptsAnyOrder() {
        boolean result = progress.completeTask(PLAYER_ID, INDEPENDENT_CHALLENGE, TASK_D.id());

        assertThat(result).isTrue();
        assertThat(progress.isCompleted(PLAYER_ID, INDEPENDENT_CHALLENGE, TASK_D.id())).isTrue();
        assertThat(progress.isCompleted(PLAYER_ID, INDEPENDENT_CHALLENGE, TASK_C.id())).isFalse();
    }

    @Test
    @DisplayName("tasksFor returns tasks in configured order with correct done flags")
    void tasksForReturnsOrderedStatus() {
        progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id());

        var statuses = progress.tasksFor(PLAYER_ID, SEQUENTIAL_CHALLENGE);

        assertThat(statuses).containsExactly(
                new TaskStatus(TASK_A, true),
                new TaskStatus(TASK_B, false));
    }

    @Test
    @DisplayName("tasksFor returns an empty list for an unknown challenge id")
    void tasksForUnknownChallengeIsEmpty() {
        assertThat(progress.tasksFor(PLAYER_ID, "unknown")).isEmpty();
    }

    @Test
    @DisplayName("completeTask on an unknown challenge id is a no-op, never throws")
    void completeTaskUnknownChallengeIsNoOp() {
        boolean result = progress.completeTask(PLAYER_ID, "unknown", TASK_A.id());

        assertThat(result).isFalse();
        verify(grantor, never()).grant(eq(PLAYER_ID), any());
    }

    @Test
    @DisplayName("completeTask on a task id not part of the challenge is a no-op, never throws")
    void completeTaskUnknownTaskIsNoOp() {
        boolean result = progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, "not_a_task");

        assertThat(result).isFalse();
        verify(grantor, never()).grant(eq(PLAYER_ID), any());
    }

    @Test
    @DisplayName("a completion is written to the current season")
    void completionIsWrittenToCurrentSeason() {
        progress.completeTask(PLAYER_ID, SEQUENTIAL_CHALLENGE, TASK_A.id());

        assertThat(repository.markCompletedScopes)
                .containsExactly(new ProgressScope(SEASON_ONE, PLAYER_ID, SEQUENTIAL_CHALLENGE, 0));
    }

    @Test
    @DisplayName("a completion recorded in one season is not visible in the next season")
    void completionIsNotVisibleInNextSeason() {
        progress.completeTask(PLAYER_ID, INDEPENDENT_CHALLENGE, TASK_D.id());

        currentSeason.switchTo(SEASON_TWO);

        assertThat(progress.isCompleted(PLAYER_ID, INDEPENDENT_CHALLENGE, TASK_D.id())).isFalse();
    }

    /** A minimal in memory fake, tracking the scopes of {@code markCompleted} calls for assertions. */
    private static final class FakeTaskCompletionRepository implements TaskCompletionRepository {
        private final Set<String> completed = new HashSet<>();
        private final List<String> markCompletedCalls = new ArrayList<>();
        private final List<ProgressScope> markCompletedScopes = new ArrayList<>();

        @Override
        public boolean isCompleted(ProgressScope scope, String taskId) {
            return completed.contains(key(scope, taskId));
        }

        @Override
        public boolean markCompleted(ProgressScope scope, String taskId) {
            markCompletedCalls.add(key(scope, taskId));
            markCompletedScopes.add(scope);
            return completed.add(key(scope, taskId));
        }

        @Override
        public void resetSeason(SeasonId season) {
            completed.removeIf(key -> key.startsWith(season.value() + "|"));
        }

        @Override
        public Map<SeasonId, Long> completedCountBySeason(UUID playerId) {
            return Map.of();
        }

        private static String key(ProgressScope scope, String taskId) {
            return scope.season().value() + "|" + scope.playerId() + "|" + scope.challengeId() + "|" + taskId;
        }
    }

    /** A {@link CurrentSeason} whose season the test can change between calls. */
    private static final class SwitchableSeason implements CurrentSeason {
        private SeasonId id;

        private SwitchableSeason(SeasonId id) {
            this.id = id;
        }

        void switchTo(SeasonId next) {
            this.id = next;
        }

        @Override
        public SeasonId id() {
            return id;
        }

        @Override
        public Optional<Season> season() {
            return Optional.empty();
        }
    }
}
