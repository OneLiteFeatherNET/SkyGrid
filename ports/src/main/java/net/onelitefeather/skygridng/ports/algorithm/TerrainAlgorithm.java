package net.onelitefeather.skygridng.ports.algorithm;

/**
 * Strategy for the whole generation algorithm, selectable by configuration.
 *
 * Each implementation decides which blocks fill a column. WFC writes solid surface terrain;
 * the SkyGrid variants write sparse blocks on a 3D grid and leave the rest as void.
 *
 * Adding an algorithm means writing one class here plus one branch in the factory. The Paper
 * chunk generator never changes.
 */
public interface TerrainAlgorithm {

    /** Stable id used in configuration and command output. */
    String id();

    /**
     * Emits all blocks for the column at (worldX, worldZ) within [worldMinY, worldMaxY].
     *
     * @param worldSeed the world seed (drives determinism)
     * @param worldMinY inclusive lowest writable Y
     * @param worldMaxY inclusive highest writable Y
     * @param sink      receives the blocks to place
     */
    void generateColumn(long worldSeed, int worldX, int worldZ, int worldMinY, int worldMaxY, ColumnSink sink);

    /** True if this algorithm uses the WFC decoration populator (sugar cane, cactus, ...). */
    default boolean usesWfcDecorations() {
        return false;
    }
}
