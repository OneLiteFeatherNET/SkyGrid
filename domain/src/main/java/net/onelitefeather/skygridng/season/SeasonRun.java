package net.onelitefeather.skygridng.season;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * The persisted runtime state of one season: its world, seed and phase. Season definitions stay in
 * config, so this record holds only what the plugin decides at runtime.
 *
 * @param seasonId  the season this run belongs to
 * @param worldKey  the namespaced key of the season world, for example {@code skygridng:season_s01}
 * @param seed      the world seed, fixed once the world is prepared so a resume recreates the same terrain
 * @param phase     the current phase
 * @param attempts  how often the preparation was tried, never negative
 * @param startedAt when the season became active, or {@code null} before that
 * @param endedAt   when the season stopped being active, or {@code null} before that
 * @param endsAt    the effective end of the season after a postponement or an advance, or {@code null} when the
 *                  configured end applies
 * @param announced the keys of the notices already sent for this run's boundary, never {@code null}
 */
public record SeasonRun(
        SeasonId seasonId,
        String worldKey,
        long seed,
        SeasonPhase phase,
        int attempts,
        Instant startedAt,
        Instant endedAt,
        Instant endsAt,
        Set<String> announced
) {

    /** The longest world key the run table accepts. */
    public static final int MAX_WORLD_KEY_LENGTH = 128;

    /** A run with no postponed end and no notice sent yet. */
    public SeasonRun(SeasonId seasonId, String worldKey, long seed, SeasonPhase phase, int attempts,
                     Instant startedAt, Instant endedAt) {
        this(seasonId, worldKey, seed, phase, attempts, startedAt, endedAt, null, Set.of());
    }

    public SeasonRun {
        if (seasonId == null) {
            throw new IllegalArgumentException("SeasonRun.seasonId must not be null");
        }
        if (worldKey == null || worldKey.isBlank()) {
            throw new IllegalArgumentException("SeasonRun.worldKey must not be blank");
        }
        if (worldKey.length() > MAX_WORLD_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "SeasonRun.worldKey must be at most " + MAX_WORLD_KEY_LENGTH + " characters");
        }
        if (phase == null) {
            throw new IllegalArgumentException("SeasonRun.phase must not be null");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("SeasonRun.attempts must not be negative");
        }
        announced = announced == null ? Set.of() : Set.copyOf(announced);
    }

    /** This run with the effective end {@code endsAt}. */
    public SeasonRun withEndsAt(Instant endsAt) {
        return new SeasonRun(seasonId, worldKey, seed, phase, attempts, startedAt, endedAt, endsAt, announced);
    }

    /** This run with the notice {@code key} recorded as sent. */
    public SeasonRun withAnnounced(String key) {
        var keys = new HashSet<>(announced);
        keys.add(key);
        return new SeasonRun(seasonId, worldKey, seed, phase, attempts, startedAt, endedAt, endsAt, keys);
    }
}
