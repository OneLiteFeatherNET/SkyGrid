package net.onelitefeather.skygridng.generator;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/**
 * In adapter: bridges Paper's {@link BiomeProvider} to the core {@link Biomes} map. It asks the core
 * which biome belongs at a coordinate and resolves the neutral id to a Bukkit {@link Biome}; the
 * palette becomes the up-front biome list Paper requires. The core owns the biome logic; this adapter
 * only translates.
 */
public final class SkyGridBiomeProvider extends BiomeProvider {

    private final Biomes biomes;
    private final BiomeResolver resolver;

    public SkyGridBiomeProvider(Biomes biomes, BiomeResolver resolver) {
        this.biomes = biomes;
        this.resolver = resolver;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return resolver.resolve(biomes.at(worldInfo.getSeed(), x, y, z));
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return biomes.palette().stream().map(resolver::resolve).distinct().toList();
    }
}
