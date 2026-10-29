package net.onelitefeather.skygridng.generator;


/**
 * In port: where the spawn adapter asks the core for a player's spread-spawn slot and the safe
 * block search parameters. The core owns the deterministic layout (an outward spiral); the adapter
 * owns the Bukkit side (assigning ordinals, searching for a safe block near the slot, and
 * teleporting the player there).
 */
public interface SpawnPoints {

    /** Whether spread-spawn is enabled; the adapter leaves the world spawn alone when false. */
    boolean spread();

    /** The spawn slot for the n-th player to join (ordinal 0, 1, 2, ...). Deterministic. */
    SpawnPoint at(int ordinal);

    /** Blocks around the assigned slot to probe when searching for a safe, standable block. */
    int searchRadius();

    /** Candidate columns to try before giving up the safe-block search. */
    int maxAttempts();
}
