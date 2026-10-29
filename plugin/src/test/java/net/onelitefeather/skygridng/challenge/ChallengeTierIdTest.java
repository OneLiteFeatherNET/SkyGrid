package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.api.model.SeasonId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link ChallengeTierId}'s compact constructor guards. The length bound
 * mirrors {@link ChallengeRule#MAX_ID_LENGTH} and the {@code challenge_id_length} check constraint
 * added in {@code V2__bound_challenge_id_length.sql}.
 */
class ChallengeTierIdTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final SeasonId SEASON = SeasonId.LEGACY;

    @Test
    @DisplayName("a null playerId throws")
    void nullPlayerIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTierId(SEASON, null, "one_life"))
                .withMessageContaining("playerId must not be null");
    }

    @Test
    @DisplayName("a blank challengeId throws")
    void blankChallengeIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTierId(SEASON, PLAYER_ID, " "))
                .withMessageContaining("challengeId must not be blank");
    }

    @Test
    @DisplayName("a challengeId at the maximum length is valid")
    void challengeIdAtMaxLengthIsValid() {
        String id = "a".repeat(ChallengeRule.MAX_ID_LENGTH);

        ChallengeTierId tierId = new ChallengeTierId(SEASON, PLAYER_ID, id);

        assertThat(tierId.challengeId()).hasSize(ChallengeRule.MAX_ID_LENGTH);
    }

    @Test
    @DisplayName("a challengeId over the maximum length throws")
    void challengeIdOverMaxLengthThrows() {
        String tooLong = "a".repeat(ChallengeRule.MAX_ID_LENGTH + 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTierId(SEASON, PLAYER_ID, tooLong))
                .withMessageContaining("challengeId must be at most " + ChallengeRule.MAX_ID_LENGTH + " characters");
    }

    @Test
    @DisplayName("a null seasonId throws")
    void nullSeasonIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTierId(null, PLAYER_ID, "one_life"))
                .withMessageContaining("seasonId must not be null");
    }
}
