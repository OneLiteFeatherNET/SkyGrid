package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.SpawnPoint;

/**
 * In port: where the spawn adapter asks the core for a player's spread-spawn slot and the platform
 * settings. The core owns the deterministic layout (an outward spiral); the adapter owns the Bukkit
 * side (assigning ordinals, building the platform, moving the player).
 */
public interface SpawnPoints {

    /** Whether spread-spawn is enabled; the adapter leaves the world spawn alone when false. */
    boolean spread();

    /** The spawn slot for the n-th player to join (ordinal 0, 1, 2, ...). Deterministic. */
    SpawnPoint at(int ordinal);

    /** The Y level the starter platform is built at. */
    int platformY();

    /** The block the starter platform is made of. */
    BlockId platformBlock();

    /** The platform extends this many blocks around the centre (0 = a single block). */
    int platformRadius();

    /** Whether a starter villager is placed on the platform, so players can reach trading. */
    boolean spawnVillager();
}
