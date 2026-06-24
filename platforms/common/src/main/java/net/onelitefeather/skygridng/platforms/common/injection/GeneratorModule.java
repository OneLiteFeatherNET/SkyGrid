package net.onelitefeather.skygridng.platforms.common.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.api.SkyGridApi;
import net.onelitefeather.skygridng.ports.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithmProvider;
import net.onelitefeather.skygridng.ports.out.RuleRepository;
import net.onelitefeather.skygridng.core.service.AlgorithmService;
import net.onelitefeather.skygridng.core.service.RegionGenerationService;
import net.onelitefeather.skygridng.core.service.RuleAdminService;
import net.onelitefeather.skygridng.core.service.SkyGridApiService;
import net.onelitefeather.skygridng.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.skygridng.core.solver.WeightedRandomCollapse;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;

/**
 * Wires the framework free core via {@code @Provides} methods. The core classes are not annotated
 * with injection annotations, so this module is the single place that constructs them, and the
 * core stays free of any DI dependency.
 *
 * In ports are bound to their service implementations here, so adapters depend only on the ports.
 */
public final class GeneratorModule extends AbstractModule {

    private static final int UNDO_HISTORY = 20;

    @Provides
    @Singleton
    RuleState provideRuleState(RuleRepository repository) {
        return new RuleState(repository.load(), UNDO_HISTORY);
    }

    @Provides
    @Singleton
    WfcSolver provideSolver() {
        return new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse());
    }

    @Provides
    @Singleton
    RegionGenerationService provideRegionService(RuleState state, WfcSolver solver) {
        return new RegionGenerationService(state, solver);
    }

    @Provides
    @Singleton
    GenerateRegionUseCase provideGenerateRegionUseCase(RegionGenerationService service) {
        return service;
    }

    @Provides
    @Singleton
    AlgorithmService provideAlgorithmService(RuleState state, GenerateRegionUseCase region) {
        return new AlgorithmService(state, region);
    }

    @Provides
    @Singleton
    TerrainAlgorithmProvider provideTerrainAlgorithmProvider(AlgorithmService service) {
        return service;
    }

    @Provides
    @Singleton
    RuleAdminService provideRuleAdminService(RuleState state, RuleRepository repository) {
        return new RuleAdminService(state, repository);
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
