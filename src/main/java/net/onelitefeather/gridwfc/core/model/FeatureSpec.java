package net.onelitefeather.gridwfc.core.model;

/**
 * A configurable material decoration of a tile (for example sugar cane, cactus).
 *
 * Pure domain: the material is a string (for example "SUGAR_CANE") so the core does not depend
 * on Bukkit. The Paper adapter resolves the name to a Material.
 *
 * @param material               block name (Bukkit material), resolved by the adapter
 * @param requiresLiquidNeighbor true if a horizontal neighbor must be liquid (for example reeds)
 * @param requiresGroundBlock    optional block name the decoration must stand on (or blank)
 * @param probability            spawn probability per column in [0,1]
 * @param minHeight              minimum column height in blocks
 * @param maxHeight              maximum column height in blocks
 */
public record FeatureSpec(
        String material,
        boolean requiresLiquidNeighbor,
        String requiresGroundBlock,
        double probability,
        int minHeight,
        int maxHeight
) {
    public FeatureSpec {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("FeatureSpec.material must not be blank");
        }
        if (minHeight < 1 || maxHeight < minHeight) {
            throw new IllegalArgumentException("Invalid heights for feature " + material);
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]: " + material);
        }
    }
}
