package net.onelitefeather.skygridng.generator;


import java.util.List;
import java.util.Set;

/**
 * The standard {@link Biomes}: a deterministic biome map built from the active preset's palette,
 * assigned in large square regions so each area has a consistent biome (and thus consistent mob
 * spawns and tints) rather than per-block noise. The region a coordinate falls in is hashed with the
 * world seed, so the map is reproducible and server independent.
 *
 * {@link #at} reads the live palette from {@link RuleState}, so switching presets changes the biomes
 * of newly generated chunks. {@link #palette} returns the union of every preset's biomes (computed
 * once at startup), because a Bukkit biome provider must declare all biomes it could ever return up
 * front, before any preset switch.
 */
public final class DefaultBiomes implements Biomes {

    /** Side length in blocks of one biome region. */
    private static final int REGION = 96;

    private final RuleState state;
    private final Set<BiomeId> declaredPalette;

    public DefaultBiomes(RuleState state, Set<BiomeId> declaredPalette) {
        this.state = state;
        this.declaredPalette = Set.copyOf(declaredPalette);
    }

    @Override
    public BiomeId at(long seed, int x, int y, int z) {
        List<BiomeId> palette = state.current().biomes();
        int regionX = Math.floorDiv(x, REGION);
        int regionZ = Math.floorDiv(z, REGION);
        long hash = Seeds.mix(seed, regionX, regionZ);
        int index = (int) Math.floorMod(hash, palette.size());
        return palette.get(index);
    }

    @Override
    public Set<BiomeId> palette() {
        return declaredPalette;
    }
}
