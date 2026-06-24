package net.onelitefeather.skygridng.core.algorithm;

import net.onelitefeather.skygridng.ports.algorithm.ColumnSink;
import net.onelitefeather.skygridng.ports.algorithm.TerrainAlgorithm;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.GenerationSettings;
import net.onelitefeather.skygridng.api.model.TileType;
import net.onelitefeather.skygridng.ports.in.GenerateRegionUseCase;

/**
 * WFC surface terrain: solid ground whose surface tile comes from the adjacency constrained
 * Wave Function Collapse region solver. This is the retained "grid era" terrain option.
 *
 * Decorations (sugar cane, cactus, flowers) are placed by the WFC populator in the adapter, not
 * here, because they need neighbor lookups during population.
 */
public final class WfcSurfaceAlgorithm implements TerrainAlgorithm {

    public static final String ID = "wfc";

    private static final BlockId BEDROCK = BlockId.of("minecraft:bedrock");
    private static final BlockId STONE = BlockId.of("minecraft:stone");

    private final GenerateRegionUseCase region;

    public WfcSurfaceAlgorithm(GenerateRegionUseCase region) {
        this.region = region;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean usesWfcDecorations() {
        return true;
    }

    @Override
    public void generateColumn(long worldSeed, int worldX, int worldZ, int worldMinY, int worldMaxY, ColumnSink sink) {
        GenerationSettings settings = region.rules().settings();
        int cellSize = settings.cellSize();
        int surfaceY = settings.surfaceY();
        int cellX = Math.floorDiv(worldX, cellSize);
        int cellZ = Math.floorDiv(worldZ, cellSize);
        TileType tile = region.tileAt(worldSeed, cellX, cellZ);

        int bedrockY = worldMinY;
        int stoneTop = Math.min(surfaceY - 3, worldMaxY);

        sink.place(bedrockY, BEDROCK);
        for (int y = bedrockY + 1; y < stoneTop; y++) {
            sink.place(y, STONE);
        }
        if (tile.liquid()) {
            for (int y = surfaceY - 3; y <= surfaceY - 2; y++) {
                sink.place(y, tile.fill());
            }
            for (int y = surfaceY - 1; y <= surfaceY; y++) {
                sink.place(y, tile.surface());
            }
        } else {
            for (int y = surfaceY - 3; y <= surfaceY - 1; y++) {
                sink.place(y, tile.fill());
            }
            sink.place(surfaceY, tile.surface());
        }
    }
}
