package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.SeasonId;

import java.util.Map;
import java.util.UUID;

/**
 * In port: reading and recording per-player challenge tier progression (called by the adapter that
 * detects challenge completion, for example the {@code one_life} elimination listener). Every call acts on
 * the current season, resolved through {@link CurrentSeason}.
 *
 * A thin front door: method shapes mirror {@link net.onelitefeather.skygridng.ports.out.ChallengeProgressRepository}
 * one-to-one, since the service behind this port is a direct delegate with no extra behavior.
 */
public interface ChallengeProgressUseCase {

    /** The player's current tier for {@code challengeId} in the current season, or 0 if never completed. */
    int tierOf(UUID playerId, String challengeId);

    /**
     * Records a completion of {@code challengeId} by {@code playerId} in the current season: creates the row
     * at tier 1 if none existed, otherwise increments the stored tier by one. Returns the new tier value.
     */
    int recordCompletion(UUID playerId, String challengeId);

    /** The player's tier per season, every season with a recorded tier. */
    Map<SeasonId, Integer> history(UUID playerId);

    /** The season-reset operation: clears every player's tier for every challenge of the current season. */
    void resetCurrentSeason();
}
