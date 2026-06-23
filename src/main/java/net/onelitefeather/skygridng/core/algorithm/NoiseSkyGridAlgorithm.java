package net.onelitefeather.skygridng.core.algorithm;

import net.onelitefeather.skygridng.core.model.SkyGridConfig;
import net.onelitefeather.skygridng.core.util.ValueNoise;
import net.onelitefeather.skygridng.core.util.WeightedTable;

/**
 * Clustered SkyGrid: same grid positions as the classic variant, but the block is chosen from a
 * seed based value noise field instead of an independent roll.
 *
 * Because nearby grid cells get similar noise values, similar blocks group into veins and themed
 * regions. Still O(1) per cell (one noise sample), with no constraint propagation, so it is much
 * faster than WFC while looking more coherent than the independent variant. This is the
 * alternative to WFC inspired by noise based procedural generation and constraint free tilers.
 */
public final class NoiseSkyGridAlgorithm implements TerrainAlgorithm {

    public static final String ID = "noise_skygrid";

    private final SkyGridConfig config;
    private final WeightedTable table;

    public NoiseSkyGridAlgorithm(SkyGridConfig config) {
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
            return;
        }
        ValueNoise noise = new ValueNoise(worldSeed ^ 0x4E4F495345L);
        double scale = config.noiseScale();
        int minY = Math.max(config.minY(), worldMinY);
        int maxY = Math.min(config.maxY(), worldMaxY);
        for (int y = firstGridLine(minY, spacing); y <= maxY; y += spacing) {
            double roll = noise.sample(worldX * scale, y * scale, worldZ * scale);
            sink.place(y, table.sample(roll));
        }
    }

    private static int firstGridLine(int minY, int spacing) {
        int mod = Math.floorMod(minY, spacing);
        return mod == 0 ? minY : minY + (spacing - mod);
    }
}
