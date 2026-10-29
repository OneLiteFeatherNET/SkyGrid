package net.onelitefeather.skygridng.generator;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wires the framework free core via {@code @Provides} methods. The core classes are not annotated
 * with injection annotations, so this module is the single place that constructs them, and the
 * core stays free of any DI dependency.
 *
 * In ports are bound to their service implementations here, so adapters depend only on the ports.
 */
public final class GeneratorModule extends AbstractModule {

    private static final int UNDO_HISTORY = 20;

    @Override
    protected void configure() {
        // The algorithm registry: one binding per algorithm, the single place new algorithms are
        // added. A distribution could contribute its own here without touching the core catalog. Both
        // factories are stateless lambdas built straight from the live configuration.
        MapBinder<String, AlgorithmFactory> algorithms =
                MapBinder.newMapBinder(binder(), String.class, AlgorithmFactory.class);
        algorithms.addBinding(IndependentSkyGridAlgorithm.ID)
                .toInstance(config -> new IndependentSkyGridAlgorithm(config.skygrid()));
        algorithms.addBinding(NoiseSkyGridAlgorithm.ID)
                .toInstance(config -> new NoiseSkyGridAlgorithm(config.skygrid()));
    }

    @Provides
    @Singleton
    PresetCatalog provideCatalog(PresetRepository repository) {
        return repository.load();
    }

    @Provides
    @Singleton
    RuleState provideRuleState(PresetCatalog catalog) {
        return new RuleState(PresetResolver.resolve(catalog, catalog.active()), UNDO_HISTORY);
    }

    @Provides
    @Singleton
    RuleCatalog provideRuleCatalog(RuleRepository repository) {
        return repository.load();
    }

    @Provides
    @Singleton
    PresetService providePresetService(PresetRepository repository, RuleState state, PresetCatalog catalog) {
        return new PresetService(repository, state, catalog);
    }

    @Provides
    @Singleton
    Presets providePresets(PresetService service) {
        return service;
    }

    @Provides
    @Singleton
    DefaultTerrainAlgorithms provideDefaultTerrainAlgorithms(RuleState state,
                                                             java.util.Map<String, AlgorithmFactory> factories) {
        return new DefaultTerrainAlgorithms(state, factories);
    }

    @Provides
    @Singleton
    TerrainAlgorithms provideTerrainAlgorithms(DefaultTerrainAlgorithms algorithms) {
        return algorithms;
    }

    @Provides
    @Singleton
    SkyGridDecorations provideSkyGridDecorations(RuleState state) {
        return new DefaultSkyGridDecorations(state);
    }

    @Provides
    @Singleton
    Set<BiomeId> provideBiomePalette(PresetCatalog catalog) {
        // The union of every preset's biomes, so any world's Bukkit biome provider can declare all of
        // them up front (a world's preset, or a runtime switch, never returns an undeclared biome).
        Set<BiomeId> union = new LinkedHashSet<>();
        for (String name : catalog.names()) {
            union.addAll(PresetResolver.resolve(catalog, name).biomes());
        }
        return union;
    }

    @Provides
    @Singleton
    Biomes provideBiomes(RuleState state, Set<BiomeId> palette) {
        return new DefaultBiomes(state, palette);
    }

    @Provides
    @Singleton
    SpawnPoints provideSpawnPoints(RuleState state) {
        return new SpiralSpawnService(state);
    }

    @Provides
    @Singleton
    RuleAdminService provideRuleAdminService(RuleState state, PresetService presets, TerrainAlgorithms algorithms) {
        return new RuleAdminService(state, presets, algorithms);
    }

    @Provides
    @Singleton
    RuleAdminUseCase provideRuleAdminUseCase(RuleAdminService service) {
        return service;
    }

    @Provides
    @Singleton
    SkyGridApi provideSkyGridApi(RuleAdminUseCase admin) {
        return new SkyGridApiService(admin);
    }
}
