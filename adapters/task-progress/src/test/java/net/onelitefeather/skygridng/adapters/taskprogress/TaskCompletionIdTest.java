package net.onelitefeather.skygridng.adapters.taskprogress;

import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link TaskCompletionId}'s compact constructor guards. The length bounds
 * mirror {@link ChallengeRule#MAX_ID_LENGTH} and {@link Task#MAX_ID_LENGTH}, and the
 * {@code char_length(...) <= 64} check constraints added in {@code V1__create_task_completion.sql}.
 */
class TaskCompletionIdTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("a null playerId throws")
    void nullPlayerIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaskCompletionId(null, "one_life", "survive_a_day"))
                .withMessageContaining("playerId must not be null");
    }

    @Test
    @DisplayName("a blank challengeId throws")
    void blankChallengeIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaskCompletionId(PLAYER_ID, " ", "survive_a_day"))
                .withMessageContaining("challengeId must not be blank");
    }

    @Test
    @DisplayName("a challengeId over the maximum length throws")
    void challengeIdOverMaxLengthThrows() {
        String tooLong = "a".repeat(ChallengeRule.MAX_ID_LENGTH + 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaskCompletionId(PLAYER_ID, tooLong, "survive_a_day"))
                .withMessageContaining("challengeId must be at most " + ChallengeRule.MAX_ID_LENGTH + " characters");
    }

    @Test
    @DisplayName("a challengeId at the maximum length is valid")
    void challengeIdAtMaxLengthIsValid() {
        String id = "a".repeat(ChallengeRule.MAX_ID_LENGTH);

        var taskCompletionId = new TaskCompletionId(PLAYER_ID, id, "survive_a_day");

        assertThat(taskCompletionId.challengeId()).hasSize(ChallengeRule.MAX_ID_LENGTH);
    }

    @Test
    @DisplayName("a blank taskId throws")
    void blankTaskIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaskCompletionId(PLAYER_ID, "one_life", " "))
                .withMessageContaining("taskId must not be blank");
    }

    @Test
    @DisplayName("a taskId over the maximum length throws")
    void taskIdOverMaxLengthThrows() {
        String tooLong = "a".repeat(Task.MAX_ID_LENGTH + 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaskCompletionId(PLAYER_ID, "one_life", tooLong))
                .withMessageContaining("taskId must be at most " + Task.MAX_ID_LENGTH + " characters");
    }

    @Test
    @DisplayName("a taskId at the maximum length is valid")
    void taskIdAtMaxLengthIsValid() {
        String id = "a".repeat(Task.MAX_ID_LENGTH);

        var taskCompletionId = new TaskCompletionId(PLAYER_ID, "one_life", id);

        assertThat(taskCompletionId.taskId()).hasSize(Task.MAX_ID_LENGTH);
    }
}
