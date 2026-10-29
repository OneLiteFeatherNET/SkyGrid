package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.SeasonId;

import java.util.Map;
import java.util.UUID;

/**
 * Out port: where the core reads and writes per-player challenge tier progression, scoped by season.
 *
 * One row per (season, player, challenge) triple. The Jackson repositories are file backed; this one is
 * database backed, but the core does not know or care which. Another backend can be added without
 * touching the core.
 */
public interface ChallengeProgressRepository {

    /** The player's tier for {@code challengeId} within {@code season}, or 0 if never completed there. */
    int tierOf(SeasonId season, UUID playerId, String challengeId);

    /**
     * Records a completion of {@code challengeId} by {@code playerId} within {@code season}: creates the
     * row at tier 1 if none existed, otherwise increments the stored tier by one. Returns the new tier.
     */
    int recordCompletion(SeasonId season, UUID playerId, String challengeId);

    /** The season-reset operation: clears every player's tier for every challenge of {@code season} only. */
    void resetSeason(SeasonId season);

    /** The player's tier per season, for every season in which the player has a row. */
    Map<SeasonId, Integer> history(UUID playerId);
}
