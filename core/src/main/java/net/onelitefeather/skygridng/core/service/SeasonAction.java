package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonRun;

import java.time.Duration;
import java.time.Instant;

/**
 * The next thing the season lifecycle must do, as decided by {@link SeasonTimeline#nextAction}. Every action
 * carries the instant it is due; {@link SeasonTimeline#isDue} tells whether that instant has passed. The
 * timeline is pure: it never reads the clock, so the caller passes the instant in.
 */
public sealed interface SeasonAction {

    /** Nothing is scheduled. */
    record None() implements SeasonAction {
    }

    /**
     * One fixed notice of the boundary {@code boundary} for the incoming season {@code season}, due at {@code at}.
     * Each (season, kind, boundary) is announced once; the caller remembers it.
     */
    record Announce(SeasonId season, AnnouncementKind kind, Instant boundary, Instant at) implements SeasonAction {
    }

    /** Creates and prepares the world of the incoming season {@code season} at {@code at}. */
    record Prepare(Season season, Instant at) implements SeasonAction {
    }

    /** Switches the active season {@code from} to the prepared season {@code to} at {@code at}. */
    record Switch(SeasonId from, SeasonId to, Instant at) implements SeasonAction {
    }

    /** Resumes a transition that was in progress when the server stopped. Always due. */
    record Resume(SeasonRun run) implements SeasonAction {
    }

    /** Warns the admins that the active season reached its end and no next season is configured. */
    record WarnNoNext(Instant at) implements SeasonAction {
    }

    /** The fixed notice offsets before a boundary. */
    enum AnnouncementKind {
        SEVEN_DAYS(Duration.ofDays(7)),
        ONE_DAY(Duration.ofHours(24)),
        ONE_HOUR(Duration.ofHours(1)),
        FIVE_MINUTES(Duration.ofMinutes(5));

        private final Duration offset;

        AnnouncementKind(Duration offset) {
            this.offset = offset;
        }

        /** How long before the boundary this notice is sent. */
        public Duration offset() {
            return offset;
        }
    }
}
