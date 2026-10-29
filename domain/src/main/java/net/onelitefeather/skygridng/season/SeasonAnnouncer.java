package net.onelitefeather.skygridng.season;


import java.time.Duration;
import java.time.Instant;

/**
 * Out port: the player and admin messages of the season lifecycle. The core passes ids, instants and
 * durations only; message keys and wording belong to the platform.
 */
public interface SeasonAnnouncer {

    /** A notice that the season {@code season} starts at the boundary {@code boundary}, {@code remaining} from now. */
    void upcoming(SeasonId season, Duration remaining, Instant boundary);

    /** The season {@code season} has started. */
    void started(SeasonId season);

    /** The end of the active season moved; {@code newEnd} is the new boundary. */
    void postponed(SeasonId season, Instant newEnd);

    /** A message for online admins and the log, for example a failed world creation. */
    void adminAlert(String message);
}
