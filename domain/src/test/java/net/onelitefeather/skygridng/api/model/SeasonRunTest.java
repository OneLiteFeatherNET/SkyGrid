package net.onelitefeather.skygridng.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** White-box tests for the {@link SeasonRun} compact constructor guards. */
class SeasonRunTest {

    private static SeasonRun run(String worldKey, int attempts) {
        return new SeasonRun(new SeasonId("s01"), worldKey, 42L, SeasonPhase.PLANNED, attempts,
                null, null);
    }

    @Test
    @DisplayName("a negative attempt count is rejected")
    void negativeAttemptsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> run("skygridng:season_s01", -1));
    }

    @Test
    @DisplayName("a blank world key is rejected")
    void blankWorldKeyIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> run(" ", 0));
    }

    @Test
    @DisplayName("a world key of 129 characters is rejected")
    void worldKeyOverMaxLengthIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> run("k".repeat(129), 0));
    }
}
