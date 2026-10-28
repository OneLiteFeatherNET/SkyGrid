package net.onelitefeather.skygridng.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * White-box tests for {@link Season}: a positive fixed duration, {@code end()} as start plus duration,
 * and a half open {@code contains} window. All instants are fixed, so no clock is read.
 */
class SeasonTest {

    private static final Instant START = Instant.parse("2026-12-01T18:00:00Z");

    private static Season season(Duration duration) {
        return new Season(new SeasonId("s01"), "Season 1", START, duration, "standard", List.of(), null);
    }

    @Test
    @DisplayName("end is the start plus the duration")
    void endIsStartPlusDuration() {
        Season season = season(Duration.ofDays(56));

        assertThat(season.end()).isEqualTo(START.plus(Duration.ofDays(56)));
    }

    @Test
    @DisplayName("a zero duration is rejected")
    void zeroDurationIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> season(Duration.ZERO));
    }

    @Test
    @DisplayName("a negative duration is rejected")
    void negativeDurationIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> season(Duration.ofHours(-1)));
    }

    @Test
    @DisplayName("contains includes the start instant")
    void containsIncludesStart() {
        assertThat(season(Duration.ofDays(1)).contains(START)).isTrue();
    }

    @Test
    @DisplayName("contains excludes the end instant")
    void containsExcludesEnd() {
        Season season = season(Duration.ofDays(1));

        assertThat(season.contains(season.end())).isFalse();
    }

    @Test
    @DisplayName("contains excludes the instant one millisecond before the start")
    void containsExcludesInstantBeforeStart() {
        assertThat(season(Duration.ofDays(1)).contains(START.minusMillis(1))).isFalse();
    }

    @Test
    @DisplayName("a blank display name defaults to the season id")
    void blankDisplayNameDefaultsToId() {
        Season season = new Season(new SeasonId("s01"), " ", START, Duration.ofDays(1), "standard", null, null);

        assertThat(season.displayName()).isEqualTo("s01");
    }
}
