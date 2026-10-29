package net.onelitefeather.skygridng.generator;

/**
 * The placement conditions of a per-block rule that grows as a decoration (a plant column such as
 * sugar cane or cactus). A rule carries a placement only when it should be placed by the decoration
 * pass; a rule without one is a plain palette-block modifier (state and physics only).
 *
 * @param structure   the 3D footprint placed around the decoration's anchor cell (never {@code null}).
 *                     This guarantees real solid support at generation time, so a gravity block (for
 *                     example the sand under sugar cane) never sits unsupported until a later block
 *                     update makes it fall.
 * @param minHeight   minimum column height in blocks (at least 1)
 * @param maxHeight   maximum column height in blocks (at least {@code minHeight})
 * @param probability placement probability per eligible column in [0,1]
 */
public record PlacementRule(
        StructurePattern structure,
        int minHeight,
        int maxHeight,
        double probability
) {
    public PlacementRule {
        if (structure == null) {
            throw new IllegalArgumentException("PlacementRule.structure must not be null");
        }
        if (minHeight < 1 || maxHeight < minHeight) {
            throw new IllegalArgumentException("invalid heights: min=" + minHeight + " max=" + maxHeight);
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]: " + probability);
        }
    }
}
