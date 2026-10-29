package net.onelitefeather.skygridng.generator;


/**
 * Places non-flowing water for self-watering plants on a SkyGrid world.
 *
 * A free water source dropped into the void flows down to the world bottom and spreads outward,
 * firing a storm of fluid block updates with no player around: server lag, and a world slowly
 * flooded by run-away water. This helper instead boxes a single source block in on the floor and all
 * four sides (the plant's own block is one wall, sand forms the other three and the floor), leaving
 * only the top open. A source with no air to flow into never ticks, so it stays put and fires no
 * update, while remaining real water the plant can drink from.
 */
final class StillWater {

    private static final int[][] NEIGHBORS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /** The block that forms the basin walls and floor. Sand keeps the look of a desert oasis. */
    private static final BlockId BASIN = BlockId.of("minecraft:sand");

    private static final BlockId WATER = BlockId.of("minecraft:water");

    private StillWater() {
    }

    /**
     * Boxes a still water source into the first cardinal neighbor of {@code (x, y, z)} that can be
     * fully contained inside the populatable region. {@code (x, y, z)} is the plant's block and acts
     * as the fourth wall. Returns {@code true} only if water was placed, so the caller can skip the
     * plant when no contained spot exists (a dry plant would just pop off and fire its own update).
     */
    static boolean place(RegionWriter writer, int x, int y, int z) {
        for (int[] offset : NEIGHBORS) {
            int wx = x + offset[0];
            int wz = z + offset[1];
            if (!canContain(writer, wx, y, wz, x, z)) {
                continue;
            }
            writer.place(wx, y - 1, wz, BASIN);              // floor
            for (int[] wall : NEIGHBORS) {                    // three walls (the plant is the fourth)
                int sx = wx + wall[0];
                int sz = wz + wall[1];
                if (sx == x && sz == z) {
                    continue;
                }
                writer.place(sx, y, sz, BASIN);
            }
            writer.place(wx, y, wz, WATER);                   // fully boxed in, only the top is open
            return true;
        }
        return false;
    }

    /** A neighbor can hold contained water only if the source, its floor and its 3 walls are all writable. */
    private static boolean canContain(RegionWriter writer, int wx, int y, int wz, int plantX, int plantZ) {
        if (!writer.isInRegion(wx, y, wz) || !writer.isInRegion(wx, y - 1, wz)) {
            return false;
        }
        for (int[] wall : NEIGHBORS) {
            int sx = wx + wall[0];
            int sz = wz + wall[1];
            if (sx == plantX && sz == plantZ) {
                continue;
            }
            if (!writer.isInRegion(sx, y, sz)) {
                return false;
            }
        }
        return true;
    }
}
