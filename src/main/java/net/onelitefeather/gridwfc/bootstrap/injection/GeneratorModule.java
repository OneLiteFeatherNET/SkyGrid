package net.onelitefeather.gridwfc.bootstrap.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Singleton;
import net.onelitefeather.gridwfc.core.port.in.GenerateRegionUseCase;
import net.onelitefeather.gridwfc.core.port.in.RuleAdminUseCase;
import net.onelitefeather.gridwfc.core.port.in.TerrainAlgorithmProvider;
import net.onelitefeather.gridwfc.core.port.out.RuleRepository;
import net.onelitefeather.gridwfc.core.service.AlgorithmService;
import net.onelitefeather.gridwfc.core.service.RegionGenerationService;
import net.onelitefeather.gridwfc.core.service.RuleAdminService;
import net.onelitefeather.gridwfc.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.gridwfc.core.solver.WeightedRandomCollapse;
import net.onelitefeather.gridwfc.core.solver.WfcSolver;
import net.onelitefeather.gridwfc.core.state.RuleState;

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
}
