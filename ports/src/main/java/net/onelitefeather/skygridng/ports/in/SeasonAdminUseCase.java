package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.SeasonCatalog;

import java.time.Duration;
import java.time.Instant;

/**
 * In port: the season lifecycle as seen by the scheduler and by admins. Every method that changes state
 * returns {@code false} when it is refused (a transition is already running, the database is unreachable,
 * or there is nothing to do), and the refusal is logged.
 */
public interface SeasonAdminUseCase {

    /** The configured seasons, including any postponement applied since the last load. */
    SeasonCatalog list();

    /**
     * Scheduler entry point, called every tick with the current instant. Runs at most one due step.
     *
     * @return {@code true} if a step ran
     */
    boolean tick(Instant now);

    /**
     * Confirmed advance: moves the end of the active season to {@code now} plus the configured minimum notice,
     * so the announcements for that boundary have the notice they need.
     */
    boolean advance(String actor, Instant now);

    /** Confirmed immediate advance: moves the end of the active season to {@code now}. Logged with the actor. */
    boolean advanceNow(String actor, Instant now);

    /** Resumes a failed transition step (a failed world creation or archive) from its persisted phase. */
    boolean retry(Instant now);

    /** Moves the end of the active season by {@code by} and shifts the following seasons that would overlap. */
    boolean postpone(Instant now, Duration by);
}
