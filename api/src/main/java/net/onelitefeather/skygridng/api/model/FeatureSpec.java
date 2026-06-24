package net.onelitefeather.skygridng.api.model;

/**
 * A configurable block decoration of a tile (for example sugar cane, cactus).
 *
 * Pure domain: the block is a platform-neutral {@link BlockId} (for example
 * {@code minecraft:sugar_cane}) so the core does not depend on any platform. The adapter resolves it.
 *
 * @param material               the decoration block, a namespaced id resolved by the adapter
 * @param requiresLiquidNeighbor true if a horizontal neighbor must be liquid (for example reeds)
 * @param requiresGroundBlock    optional block the decoration must stand on, or {@code null} for none
 * @param probability            spawn probability per column in [0,1]
 * @param minHeight              minimum column height in blocks
 * @param maxHeight              maximum column height in blocks
 */
public record FeatureSpec(
        BlockId material,
        boolean requiresLiquidNeighbor,
        BlockId requiresGroundBlock,
        double probability,
        int minHeight,
        int maxHeight
) {
    public FeatureSpec {
        if (material == null) {
            throw new IllegalArgumentException("FeatureSpec.material must not be null");
        }
        if (minHeight < 1 || maxHeight < minHeight) {
            throw new IllegalArgumentException("Invalid heights for feature " + material);
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]: " + material);
        }
    }
}
