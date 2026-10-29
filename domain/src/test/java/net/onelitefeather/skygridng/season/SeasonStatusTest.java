package net.onelitefeather.skygridng.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** White-box tests for {@link SeasonStatus}: the active season and phase are required, the next season is optional. */
class SeasonStatusTest {

    @Test
    @DisplayName("a status without an active season is rejected")
    void missingActiveSeasonIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SeasonStatus(null, SeasonPhase.ACTIVE, null, null));
    }

    @Test
    @DisplayName("a status without a next season is accepted when no next season is configured")
    void absentNextSeasonIsAccepted() {
        SeasonStatus status = new SeasonStatus(SeasonId.LEGACY, SeasonPhase.ACTIVE, null, null);

        assertThat(status.nextSeason()).isNull();
        assertThat(status.nextActionAt()).isNull();
    }
}
