package net.onelitefeather.skygridng.platforms.common;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.platforms.common.generator.HexagonalChunkGenerator;
import net.onelitefeather.skygridng.api.model.BiomeId;
import net.onelitefeather.skygridng.api.model.RuleCatalog;
import net.onelitefeather.skygridng.core.service.DefaultBiomes;
import net.onelitefeather.skygridng.core.service.DefaultSkyGridDecorations;
import net.onelitefeather.skygridng.core.service.DefaultTerrainAlgorithms;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.ports.algorithm.AlgorithmFactory;

import java.util.Map;
import java.util.Set;

/**
 * Builds a complete per-world chunk generator from a single {@link RuleState}.
 *
 * Every world that selects a preset through its generator spec gets its own generator graph reading
 * that preset's live state, so editing the preset propagates to the world. The same algorithm
 * factory registry as the default generator is reused (the factories build straight from the
 * configuration, so they work for any state), and the biome palette is the catalog-wide union so a
 * Bukkit biome provider can declare every biome any preset could produce.
 */
@Singleton
public final class WorldGeneratorFactory {

    private final Map<String, AlgorithmFactory> factories;
    private final Set<BiomeId> biomePalette;
    private final RuleCatalog ruleCatalog;

    @Inject
    public WorldGeneratorFactory(Map<String, AlgorithmFactory> factories, Set<BiomeId> biomePalette,
                                 RuleCatalog ruleCatalog) {
        this.factories = factories;
        this.biomePalette = biomePalette;
        this.ruleCatalog = ruleCatalog;
    }

    public HexagonalChunkGenerator build(RuleState state) {
        DefaultTerrainAlgorithms algorithms = new DefaultTerrainAlgorithms(state, factories);
        DefaultSkyGridDecorations decorations = new DefaultSkyGridDecorations(state);
        DefaultBiomes biomes = new DefaultBiomes(state, biomePalette);
        return new HexagonalChunkGenerator(algorithms, decorations, biomes, ruleCatalog);
    }
}
