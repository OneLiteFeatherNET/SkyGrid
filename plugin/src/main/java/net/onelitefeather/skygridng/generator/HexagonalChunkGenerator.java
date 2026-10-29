package net.onelitefeather.skygridng.generator;

import jakarta.inject.Inject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
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
 * algorithms need.
 */
public final class HexagonalChunkGenerator extends ChunkGenerator {

    private final DefaultTerrainAlgorithms algorithms;
    private final SkyGridDecorations decorations;
    private final DefaultBiomes biomes;
    private final RuleResolver rules;
    private final BiomeResolver biomeResolver = new BiomeResolver(Biome.PLAINS);

    @Inject
    public HexagonalChunkGenerator(DefaultTerrainAlgorithms algorithms, SkyGridDecorations decorations, DefaultBiomes biomes,
                                   RuleCatalog ruleCatalog) {
        this.algorithms = algorithms;
        this.decorations = decorations;
        this.biomes = biomes;
        this.rules = new RuleResolver(ruleCatalog, Material.STONE);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new SkyGridBiomeProvider(biomes, biomeResolver);
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
                        chunkData.setBlock(lx, y, lz, rules.blockData(material));
                    }
                });
            }
        }
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        if (decorations.active() || !rules.decorations().isEmpty()) {
            return List.of(new SkyGridDecorationPopulator(decorations, rules));
        }
        return List.of();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        // Paper's one-time world-spawn concept, set before any chunk or player exists; it is
        // intentionally not wired to the safe-location search, which needs live block reads.
        return new Location(world, 0.5, 80.0, 0.5);
    }
}
