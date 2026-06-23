package net.onelitefeather.skygridng.adapter.in.paper;

import net.onelitefeather.skygridng.core.model.FeatureSpec;
import net.onelitefeather.skygridng.core.model.GenerationSettings;
import net.onelitefeather.skygridng.core.model.TileType;
import net.onelitefeather.skygridng.core.port.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.core.util.Seeds;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * In adapter: places the {@link FeatureSpec} decorations defined on a WFC tile.
 *
 * Fully data driven: what spawns, how tall, and under which conditions comes from the JSON
 * configuration. The adapter uses a per cell deterministic RNG so the result is independent of
 * population order. Only used when the WFC algorithm is active.
 */
public final class MaterialPopulator extends BlockPopulator {

    private static final int[][] NEIGHBORS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private final GenerateRegionUseCase region;
    private final MaterialResolver materials;

    public MaterialPopulator(GenerateRegionUseCase region, MaterialResolver materials) {
        this.region = region;
        this.materials = materials;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion area) {
        long seed = worldInfo.getSeed();
        GenerationSettings settings = region.rules().settings();
        int cellSize = settings.cellSize();
        int surfaceY = settings.surfaceY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX * 16 + x;
                int globalZ = chunkZ * 16 + z;
                int cellX = Math.floorDiv(globalX, cellSize);
                int cellZ = Math.floorDiv(globalZ, cellSize);
                TileType tile = region.tileAt(seed, cellX, cellZ);
                if (tile.features().isEmpty()) {
                    continue;
                }
                RandomGenerator rng = Seeds.rng(Seeds.cellSeed(seed, globalX, globalZ));
                for (FeatureSpec feature : tile.features()) {
                    placeFeature(area, feature, globalX, surfaceY, globalZ, rng);
                }
            }
        }
    }

    private void placeFeature(
            LimitedRegion area, FeatureSpec feature, int x, int surfaceY, int z, RandomGenerator rng) {
        if (rng.nextDouble() > feature.probability()) {
            return;
        }
        if (!area.isInRegion(x, surfaceY + 1, z)) {
            return;
        }
        if (!feature.requiresGroundBlock().isBlank()) {
            Material ground = materials.resolve(feature.requiresGroundBlock());
            if (area.getType(x, surfaceY, z) != ground) {
                return;
            }
        }
        if (feature.requiresLiquidNeighbor() && !hasLiquidNeighbor(area, x, surfaceY, z)) {
            return;
        }

        Material material = materials.resolve(feature.material());
        int height = feature.minHeight() + rng.nextInt(feature.maxHeight() - feature.minHeight() + 1);
        for (int h = 1; h <= height; h++) {
            int y = surfaceY + h;
            if (!area.isInRegion(x, y, z)) {
                break;
            }
            area.setType(x, y, z, material);
        }
    }

    private boolean hasLiquidNeighbor(LimitedRegion area, int x, int y, int z) {
        for (int[] offset : NEIGHBORS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            if (area.isInRegion(nx, y, nz)) {
                Material type = area.getType(nx, y, nz);
                if (type == Material.WATER || type == Material.LAVA) {
                    return true;
                }
            }
        }
        return false;
    }
}
