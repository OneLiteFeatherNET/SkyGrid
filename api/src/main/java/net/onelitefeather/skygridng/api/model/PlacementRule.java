package net.onelitefeather.skygridng.api.model;

/**
 * The placement conditions of a per-block rule that grows as a decoration (a plant column such as
 * sugar cane or cactus). A rule carries a placement only when it should be placed by the decoration
 * pass; a rule without one is a plain palette-block modifier (state and physics only).
 *
 * @param requiresGround         optional block the plant must stand on (also the support the adapter
 *                               places for a self-contained plant), or {@code null} for none
 * @param requiresLiquidNeighbor true if a horizontal neighbor of the support must be liquid
 * @param water                  how the rule provides water for the plant (never {@code null};
 *                               defaults to {@link WaterMode#NONE})
 * @param minHeight              minimum column height in blocks (at least 1)
 * @param maxHeight              maximum column height in blocks (at least {@code minHeight})
 * @param probability            placement probability per eligible column in [0,1]
 */
public record PlacementRule(
        BlockId requiresGround,
        boolean requiresLiquidNeighbor,
        WaterMode water,
        int minHeight,
        int maxHeight,
        double probability
) {
    public PlacementRule {
        water = water == null ? WaterMode.NONE : water;
        if (minHeight < 1 || maxHeight < minHeight) {
            throw new IllegalArgumentException("invalid heights: min=" + minHeight + " max=" + maxHeight);
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]: " + probability);
        }
    }
}
