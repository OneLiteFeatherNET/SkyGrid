package net.onelitefeather.skygridng.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link ChallengeTier}'s compact constructor guards: a non-null
 * {@code playerId}, a non-blank {@code challengeId}, and a non-negative {@code tier} (0 meaning
 * never completed). Pure domain logic in the {@code api} module: no Bukkit, no Jackson.
 */
class ChallengeTierTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("a well formed tier constructs without throwing and exposes its accessors")
    void validConstructionSucceeds() {
        ChallengeTier tier = new ChallengeTier(PLAYER_ID, "one_life", 3);

        assertThat(tier.playerId()).isEqualTo(PLAYER_ID);
        assertThat(tier.challengeId()).isEqualTo("one_life");
        assertThat(tier.tier()).isEqualTo(3);
    }

    @Test
    @DisplayName("tier 0 is valid, meaning the player never completed the challenge")
    void zeroTierIsValid() {
        ChallengeTier tier = new ChallengeTier(PLAYER_ID, "one_life", 0);

        assertThat(tier.tier()).isZero();
    }

    @Test
    @DisplayName("a null playerId throws")
    void nullPlayerIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTier(null, "one_life", 0))
                .withMessageContaining("playerId must not be null");
    }

    @Test
    @DisplayName("a null challengeId throws")
    void nullChallengeIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTier(PLAYER_ID, null, 0))
                .withMessageContaining("challengeId must not be blank");
    }

    @Test
    @DisplayName("a blank challengeId throws")
    void blankChallengeIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTier(PLAYER_ID, "   ", 0))
                .withMessageContaining("challengeId must not be blank");
    }

    @Test
    @DisplayName("a negative tier throws")
    void negativeTierThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTier(PLAYER_ID, "one_life", -1))
                .withMessageContaining("tier must not be negative");
    }

    @Test
    @DisplayName("a challengeId at the maximum length is valid")
    void challengeIdAtMaxLengthIsValid() {
        String id = "a".repeat(ChallengeRule.MAX_ID_LENGTH);

        ChallengeTier tier = new ChallengeTier(PLAYER_ID, id, 0);

        assertThat(tier.challengeId()).hasSize(ChallengeRule.MAX_ID_LENGTH);
    }

    @Test
    @DisplayName("a challengeId over the maximum length throws")
    void challengeIdOverMaxLengthThrows() {
        String tooLong = "a".repeat(ChallengeRule.MAX_ID_LENGTH + 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeTier(PLAYER_ID, tooLong, 0))
                .withMessageContaining("challengeId must be at most " + ChallengeRule.MAX_ID_LENGTH + " characters");
    }
}
