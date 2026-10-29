package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.BiomeId;

import java.util.Set;

/**
 * In port: the deterministic biome map of the world.
 *
 * The generator adapter asks which biome belongs at a coordinate (the core decides, deterministically
 * from the world seed) and which biomes can appear at all (the palette, so the platform can declare
 * them up front). Biomes are platform-neutral {@link BiomeId}s; the adapter resolves them. The biome
 * drives mob spawning and grass/water tints even on a bare floating grid.
 */
public interface Biomes {

    /** The biome at a world coordinate, deterministic for a given seed. */
    BiomeId at(long seed, int x, int y, int z);

    /** Every biome {@link #at} can return; a platform must declare these up front. */
    Set<BiomeId> palette();
}
