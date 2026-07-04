package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.RuleCatalog;
import net.onelitefeather.skygridng.ports.algorithm.TerrainAlgorithm;
import net.onelitefeather.skygridng.ports.in.Biomes;
import net.onelitefeather.skygridng.ports.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.ports.in.SkyGridDecorations;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
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
 * algorithms need. The WFC algorithm fills solid ground instead.
 */
public final class HexagonalChunkGenerator extends ChunkGenerator {

    private final TerrainAlgorithms algorithms;
    private final GenerateRegionUseCase region;
    private final SkyGridDecorations decorations;
    private final Biomes biomes;
    private final RuleResolver rules;
    private final BiomeResolver biomeResolver = new BiomeResolver(Biome.PLAINS);

    @Inject
    public HexagonalChunkGenerator(TerrainAlgorithms algorithms, GenerateRegionUseCase region,
                                   SkyGridDecorations decorations, Biomes biomes, RuleCatalog ruleCatalog) {
        this.algorithms = algorithms;
        this.region = region;
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
        if (algorithms.current().usesWfcDecorations()) {
            return List.of(new MaterialPopulator(region, rules));
        }
        if (decorations.active() || !rules.decorations().isEmpty()) {
            return List.of(new SkyGridDecorationPopulator(decorations, rules));
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
