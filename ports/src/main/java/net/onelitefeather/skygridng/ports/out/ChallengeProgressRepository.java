package net.onelitefeather.skygridng.ports.out;

import java.util.UUID;

/**
 * Out port: where the core reads and writes per-player challenge tier progression.
 *
 * One row per (player, challenge) pair. The Jackson repositories are file backed; this one is
 * expected to be database backed (the first such adapter in the project), but the core does not know
 * or care which. Another backend can be added without touching the core.
 */
public interface ChallengeProgressRepository {

    /** The player's current tier for {@code challengeId}, or 0 if never completed. */
    int tierOf(UUID playerId, String challengeId);

    /**
     * Records a completion of {@code challengeId} by {@code playerId}: creates the row at tier 1 if
     * none existed, otherwise increments the stored tier by one. Returns the new tier value.
     */
    int recordCompletion(UUID playerId, String challengeId);

    /** The season-reset operation: clears every player's tier for every challenge. */
    void resetAll();
}
