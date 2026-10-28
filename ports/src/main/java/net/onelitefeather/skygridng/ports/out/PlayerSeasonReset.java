package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.SeasonId;

import java.util.UUID;

/**
 * Out port: resets one player's vanilla state on the first entry into a season (inventory, ender chest, XP,
 * health, hunger, effects, respawn point, game mode). The platform keeps a per-season marker so a second
 * entry into the same season does nothing.
 */
public interface PlayerSeasonReset {

    /** Resets {@code playerId} for {@code season} if the player has not been reset for it yet. */
    void reset(UUID playerId, SeasonId season);
}
