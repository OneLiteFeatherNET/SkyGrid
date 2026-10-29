package net.onelitefeather.skygridng.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link SeasonId}: the id pattern {@code [a-z0-9_]{1,64}} and the implicit
 * {@link SeasonId#LEGACY} season.
 */
class SeasonIdTest {

    @Test
    @DisplayName("a lower case id with digits and underscores is accepted")
    void validIdIsAccepted() {
        assertThat(new SeasonId("s01_overgrowth").value()).isEqualTo("s01_overgrowth");
    }

    @Test
    @DisplayName("an empty id is rejected")
    void emptyIdIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SeasonId(""));
    }

    @Test
    @DisplayName("an id of 64 characters is accepted")
    void idAtMaxLengthIsAccepted() {
        assertThat(new SeasonId("a".repeat(64)).value()).hasSize(64);
    }

    @Test
    @DisplayName("an id of 65 characters is rejected")
    void idOverMaxLengthIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SeasonId("a".repeat(65)));
    }

    @Test
    @DisplayName("an upper case id is rejected")
    void upperCaseIdIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SeasonId("S01"));
    }

    @Test
    @DisplayName("the legacy season id is the value legacy")
    void legacyIdHasValueLegacy() {
        assertThat(SeasonId.LEGACY.value()).isEqualTo("legacy");
    }
}
