package net.onelitefeather.skygridng.season;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * One season definition from {@code seasons/<id>.<ext>}: a fixed length window that runs one preset and
 * one challenge set. Seasons are config, so this record is the single source of truth for their shape.
 *
 * @param id          the season id, authoritative over the file name
 * @param displayName the name shown to players; a blank value defaults to the id
 * @param start       the first instant of the season (inclusive)
 * @param duration    the fixed, positive length of the season
 * @param preset      the preset the season world is generated with
 * @param challenges  the challenge ids active during the season; an immutable list, possibly empty
 * @param seed        a fixed world seed, or {@code null} for a random seed at prepare time
 */
public record Season(
        SeasonId id,
        String displayName,
        Instant start,
        Duration duration,
        String preset,
        List<String> challenges,
        Long seed
) {

    public Season {
        if (id == null) {
            throw new IllegalArgumentException("Season.id must not be null");
        }
        if (start == null) {
            throw new IllegalArgumentException("Season.start must not be null");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Season.duration must be positive");
        }
        if (preset == null || preset.isBlank()) {
            throw new IllegalArgumentException("Season.preset must not be blank");
        }
        displayName = displayName == null || displayName.isBlank() ? id.value() : displayName;
        challenges = challenges == null ? List.of() : List.copyOf(challenges);
    }

    /** The first instant after the season, {@code start} plus {@code duration}. */
    public Instant end() {
        return start.plus(duration);
    }

    /** True if {@code instant} is inside the season: the start is inclusive, the end is exclusive. */
    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end());
    }
}
