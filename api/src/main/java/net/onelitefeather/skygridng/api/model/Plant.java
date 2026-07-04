package net.onelitefeather.skygridng.api.model;

/**
 * A plant decoration that grows in a column (for example sugar cane or cactus).
 *
 * @param material               the plant block, a namespaced id resolved by the adapter
 * @param requiresLiquidNeighbor true if a horizontal neighbor of the support must be liquid (reeds)
 * @param requiresGroundBlock    optional block the plant must stand on (also the support placed for a
 *                               self-contained plant), or {@code null} for none
 * @param probability            spawn probability per column in [0,1]
 * @param minHeight              minimum column height in blocks
 * @param maxHeight              maximum column height in blocks
 * @param providesWater          if true the adapter places its own water source next to the support,
 *                               so the plant is self-contained (works on the sparse classic grid)
 */
public record Plant(
        BlockId material,
        boolean requiresLiquidNeighbor,
        BlockId requiresGroundBlock,
        double probability,
        int minHeight,
        int maxHeight,
        boolean providesWater
) implements Decoration {
    public Plant {
        if (material == null) {
            throw new IllegalArgumentException("Plant.material must not be null");
        }
        if (minHeight < 1 || maxHeight < minHeight) {
            throw new IllegalArgumentException("Invalid heights for plant " + material);
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]: " + material);
        }
    }
}
