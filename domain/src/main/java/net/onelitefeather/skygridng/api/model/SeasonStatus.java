package net.onelitefeather.skygridng.api.model;

import java.time.Instant;

/**
 * A read only snapshot of the season lifecycle for {@code /skygrid season status}: the active season and
 * its phase, plus the next season and the next scheduled action with its instant when one exists.
 *
 * @param activeSeason the season that is running now
 * @param phase        the phase of the active season's run
 * @param nextSeason   the season that follows, or {@code null} when none is configured
 * @param nextActionAt the instant of the next scheduled action, or {@code null} when none is scheduled
 */
public record SeasonStatus(SeasonId activeSeason, SeasonPhase phase, SeasonId nextSeason, Instant nextActionAt) {

    public SeasonStatus {
        if (activeSeason == null) {
            throw new IllegalArgumentException("SeasonStatus.activeSeason must not be null");
        }
        if (phase == null) {
            throw new IllegalArgumentException("SeasonStatus.phase must not be null");
        }
    }
}
