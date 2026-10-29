package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.Reward;

import java.util.UUID;

/**
 * Out port: grants a {@link Reward} to a player. Bukkit-aware only in its implementation (built in
 * {@code platforms.olf.challenge}, an exhaustive switch over {@link Reward}'s permitted record shapes),
 * framework-free here.
 */
public interface RewardGrantor {

    /** Grants {@code reward} to {@code playerId}. */
    void grant(UUID playerId, Reward reward);
}
