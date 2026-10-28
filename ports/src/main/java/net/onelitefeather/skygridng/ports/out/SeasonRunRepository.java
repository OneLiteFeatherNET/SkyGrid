package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonRun;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Out port: the persisted runtime state of the season runs (world, seed, phase). Season definitions stay in
 * config; this port holds only what the plugin decides at runtime. Writes that must never leave two seasons
 * active at once go through {@link #switchActive}, which is one unit of work.
 */
public interface SeasonRunRepository {

    /** The run of {@code seasonId}, empty when no run exists for it. */
    Optional<SeasonRun> find(SeasonId seasonId);

    /** The single run in phase {@code ACTIVE}, empty when no season is active. */
    Optional<SeasonRun> active();

    /** Every run that is not {@code ACTIVE} and not {@code ARCHIVED}: the runs a restart must resume. */
    List<SeasonRun> inFlight();

    /**
     * Writes {@code run}, inserting it when it is new and replacing its stored state otherwise.
     *
     * @throws SeasonRunConflictException if the write conflicts with the stored state
     */
    void save(SeasonRun run);

    /**
     * Moves {@code from} to {@code CLOSING} with its end instant {@code at}, then {@code to} to {@code ACTIVE}
     * with its start instant {@code at}, in one unit of work. Either both changes commit or neither does.
     *
     * @throws SeasonRunConflictException if either run is missing or the switch would break the single active rule
     */
    void switchActive(SeasonId from, SeasonId to, Instant at);
}
