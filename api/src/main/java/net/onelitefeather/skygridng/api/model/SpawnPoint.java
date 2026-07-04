package net.onelitefeather.skygridng.api.model;

/**
 * A horizontal spawn slot in block coordinates. The Y level comes from {@link SpawnConfig#platformY()};
 * the adapter places the player just above the starter platform.
 */
public record SpawnPoint(int x, int z) {
}
