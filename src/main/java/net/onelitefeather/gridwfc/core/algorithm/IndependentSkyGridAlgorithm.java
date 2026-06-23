package net.onelitefeather.gridwfc.core.algorithm;

import net.onelitefeather.gridwfc.core.model.SkyGridConfig;
import net.onelitefeather.gridwfc.core.util.Seeds;
import net.onelitefeather.gridwfc.core.util.WeightedTable;

/**
 * Classic SkyGrid: a single block on every grid point, air everywhere else, floating in the void.
 *
 * Each grid cell is sampled independently from a weighted table using a hash of (seed, x, y, z).
 * This is O(1) per cell, has no constraint propagation and no shared state, so it is the fastest
 * option and is trivially parallel and deterministic. Faithful to SethBling's 2012 SkyGrid.
 */
public final class IndependentSkyGridAlgorithm implements TerrainAlgorithm {

    public static final String ID = "skygrid";

    private final SkyGridConfig config;
    private final WeightedTable table;

    public IndependentSkyGridAlgorithm(SkyGridConfig config) {
        this.config = config;
        this.table = WeightedTable.of(config.blocks());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void generateColumn(long worldSeed, int worldX, int worldZ, int worldMinY, int worldMaxY, ColumnSink sink) {
        int spacing = config.spacing();
        if (Math.floorMod(worldX, spacing) != 0 || Math.floorMod(worldZ, spacing) != 0) {
            return; // not on a grid line: pure void column
        }
        int minY = Math.max(config.minY(), worldMinY);
        int maxY = Math.min(config.maxY(), worldMaxY);
        for (int y = firstGridLine(minY, spacing); y <= maxY; y += spacing) {
            double roll = Seeds.unitDouble(Seeds.blockSeed(worldSeed, worldX, y, worldZ));
            sink.place(y, table.sample(roll));
        }
    }

    private static int firstGridLine(int minY, int spacing) {
        int mod = Math.floorMod(minY, spacing);
        return mod == 0 ? minY : minY + (spacing - mod);
    }
}
