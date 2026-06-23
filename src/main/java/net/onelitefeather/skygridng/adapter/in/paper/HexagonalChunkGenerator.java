package net.onelitefeather.skygridng.adapter.in.paper;

import net.onelitefeather.skygridng.core.algorithm.TerrainAlgorithm;
import net.onelitefeather.skygridng.core.port.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.core.port.in.TerrainAlgorithmProvider;
import jakarta.inject.Inject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * In adapter: bridges Paper's {@link ChunkGenerator} and the selected {@link TerrainAlgorithm}.
 *
 * The adapter holds no generation logic. It asks the active algorithm to emit a column and writes
 * the resolved blocks into the chunk. Everything else stays void, which is what the SkyGrid
 * algorithms need. The WFC algorithm fills solid ground instead.
 */
public final class HexagonalChunkGenerator extends ChunkGenerator {

    private final TerrainAlgorithmProvider algorithms;
    private final GenerateRegionUseCase region;
    private final MaterialResolver materials = new MaterialResolver(Material.STONE);

    @Inject
    public HexagonalChunkGenerator(TerrainAlgorithmProvider algorithms, GenerateRegionUseCase region) {
        this.algorithms = algorithms;
        this.region = region;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        TerrainAlgorithm algorithm = algorithms.current();
        int minY = chunkData.getMinHeight();
        int maxY = chunkData.getMaxHeight() - 1;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX * 16 + x;
                int globalZ = chunkZ * 16 + z;
                final int lx = x;
                final int lz = z;
                algorithm.generateColumn(seed, globalX, globalZ, minY, maxY, (y, material) -> {
                    if (y >= minY && y <= maxY) {
                        chunkData.setBlock(lx, y, lz, materials.resolve(material));
                    }
                });
            }
        }
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        if (algorithms.current().usesWfcDecorations()) {
            return List.of(new MaterialPopulator(region, materials));
        }
        return List.of();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        if (algorithms.current().usesWfcDecorations()) {
            int surfaceY = region.rules().settings().surfaceY();
            return new Location(world, 0.5, surfaceY + 1.0, 0.5);
        }
        return new Location(world, 0.5, 80.0, 0.5);
    }
}
