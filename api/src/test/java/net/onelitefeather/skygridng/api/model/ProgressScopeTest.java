package net.onelitefeather.skygridng.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * White-box tests for {@link ProgressScope}: the prestige step is bounded to 0 through 10 and the
 * challenge id follows the {@link ChallengeRule} rules.
 */
class ProgressScopeTest {

    private static final SeasonId SEASON = new SeasonId("s01");
    private static final UUID PLAYER = new UUID(0L, 1L);

    @Test
    @DisplayName("prestige step 0 is accepted")
    void prestigeStepZeroIsAccepted() {
        assertThatNoException().isThrownBy(() -> new ProgressScope(SEASON, PLAYER, "one_life", 0));
    }

    @Test
    @DisplayName("prestige step 10 is accepted")
    void prestigeStepTenIsAccepted() {
        assertThatNoException().isThrownBy(() -> new ProgressScope(SEASON, PLAYER, "one_life", 10));
    }

    @Test
    @DisplayName("prestige step 11 is rejected")
    void prestigeStepElevenIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProgressScope(SEASON, PLAYER, "one_life", 11));
    }

    @Test
    @DisplayName("a negative prestige step is rejected")
    void negativePrestigeStepIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProgressScope(SEASON, PLAYER, "one_life", -1));
    }

    @Test
    @DisplayName("a blank challenge id is rejected")
    void blankChallengeIdIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProgressScope(SEASON, PLAYER, " ", 0));
    }
}
