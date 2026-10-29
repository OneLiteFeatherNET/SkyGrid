package net.onelitefeather.skygridng.challenge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link ChallengeRule}'s compact constructor guards: a non-blank, length
 * bounded {@code id}, and a {@code null} description defaulting to empty.
 */
class ChallengeRuleTest {

    @Test
    @DisplayName("a well formed rule constructs without throwing and exposes its accessors")
    void validConstructionSucceeds() {
        ChallengeRule rule = new ChallengeRule("one_life", "one life only", true);

        assertThat(rule.id()).isEqualTo("one_life");
        assertThat(rule.description()).isEqualTo("one life only");
        assertThat(rule.active()).isTrue();
    }

    @Test
    @DisplayName("a null description defaults to empty")
    void nullDescriptionDefaultsToEmpty() {
        ChallengeRule rule = new ChallengeRule("one_life", null, false);

        assertThat(rule.description()).isEmpty();
    }

    @Test
    @DisplayName("a blank id throws")
    void blankIdThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeRule("   ", "desc", false))
                .withMessageContaining("id must not be blank");
    }

    @Test
    @DisplayName("an id at the maximum length is valid")
    void idAtMaxLengthIsValid() {
        String id = "a".repeat(ChallengeRule.MAX_ID_LENGTH);

        ChallengeRule rule = new ChallengeRule(id, "desc", false);

        assertThat(rule.id()).hasSize(ChallengeRule.MAX_ID_LENGTH);
    }

    @Test
    @DisplayName("an id over the maximum length throws")
    void idOverMaxLengthThrows() {
        String tooLong = "a".repeat(ChallengeRule.MAX_ID_LENGTH + 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChallengeRule(tooLong, "desc", false))
                .withMessageContaining("id must be at most " + ChallengeRule.MAX_ID_LENGTH + " characters");
    }

    @Test
    @DisplayName("withActive returns a copy with only the active flag changed")
    void withActiveCopiesOtherFields() {
        ChallengeRule rule = new ChallengeRule("one_life", "desc", false);

        ChallengeRule activated = rule.withActive(true);

        assertThat(activated.id()).isEqualTo("one_life");
        assertThat(activated.description()).isEqualTo("desc");
        assertThat(activated.active()).isTrue();
    }
}
