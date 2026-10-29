package net.onelitefeather.skygridng.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** White-box tests for {@link SeasonSettings}: the documented defaults and the non-negative durations. */
class SeasonSettingsTest {

    @Test
    @DisplayName("the defaults are a 15 minute prepare lead, the archive directory and a 24 hour minimum notice")
    void defaultsMatchTheDocumentedValues() {
        SeasonSettings defaults = SeasonSettings.defaults();

        assertThat(defaults.prepareLead()).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.archiveDirectory()).isEqualTo("archive");
        assertThat(defaults.advanceMinNotice()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("a negative prepare lead is rejected")
    void negativePrepareLeadIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SeasonSettings(
                Duration.ofMinutes(-1), "archive", Duration.ofHours(24)));
    }
}
