package net.onelitefeather.skygridng.generator;

/**
 * A horizontal spawn slot in block coordinates. The adapter searches near this column for a safe,
 * standable block (bounded by {@link SpawnConfig#searchRadius()} and {@link SpawnConfig#maxAttempts()})
 * and teleports the player there.
 */
public record SpawnPoint(int x, int z) {
}
