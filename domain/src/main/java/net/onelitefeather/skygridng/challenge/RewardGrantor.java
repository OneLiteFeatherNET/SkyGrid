package net.onelitefeather.skygridng.challenge;


import java.util.UUID;

/**
 * Out port: grants a {@link Reward} to a player. Bukkit-aware only in its implementation (built in
 * {@code challenge}, an exhaustive switch over {@link Reward}'s permitted record shapes),
 * framework-free here.
 */
public interface RewardGrantor {

    /** Grants {@code reward} to {@code playerId}. */
    void grant(UUID playerId, Reward reward);
}
