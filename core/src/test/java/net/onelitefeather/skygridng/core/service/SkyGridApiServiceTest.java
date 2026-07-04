package net.onelitefeather.skygridng.core.service;

import net.kyori.adventure.text.TranslatableComponent;
import net.onelitefeather.skygridng.api.SkyGridApi;
import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import net.onelitefeather.skygridng.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.skygridng.core.solver.WeightedRandomCollapse;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the public API facade. It must read through to the admin use case without
 * exposing any mutation, so the same snapshot the admin owns is what callers observe.
 */
class SkyGridApiServiceTest {

    private RuleAdminUseCase admin;
    private SkyGridApi api;

    @BeforeEach
    void setUp() {
        PresetCatalog catalog = Fixtures.presetCatalog("skygrid");
        Fixtures.InMemoryPresetRepository repository = new Fixtures.InMemoryPresetRepository(catalog);
        RuleState state = new RuleState(PresetResolver.resolve(catalog, catalog.active()), 8);
        PresetService presets = new PresetService(repository, state, catalog);
        RegionGenerationService region = new RegionGenerationService(state,
                new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
        DefaultTerrainAlgorithms algorithms = new DefaultTerrainAlgorithms(state,
                Fixtures.defaultAlgorithmFactories(), region);
        admin = new RuleAdminService(state, presets, algorithms);
        api = new SkyGridApiService(admin);
    }

    @Test
    @DisplayName("the facade reads through to the admin use case")
    void readsThroughToAdmin() {
        assertThat(api.algorithm()).isEqualTo(admin.algorithm());
        assertThat(api.configuration()).isEqualTo(admin.current());
        assertThat(api.tileIds()).isEqualTo(admin.tileIds());
    }

    @Test
    @DisplayName("a known tile resolves and an unknown one is empty")
    void resolvesTiles() {
        String knownId = admin.tileIds().getFirst();

        assertThat(api.tile(knownId)).contains(admin.tile(knownId).orElseThrow());
        assertThat(api.tile("does_not_exist")).isEmpty();
    }

    @Test
    @DisplayName("the api version is exposed")
    void exposesApiVersion() {
        assertThat(api.apiVersion()).isEqualTo(SkyGridApi.API_VERSION);
    }

    @Test
    @DisplayName("describe() builds a translatable Adventure summary in the core")
    void describeIsATranslatableSummary() {
        assertThat(api.describe()).isInstanceOfSatisfying(TranslatableComponent.class, summary -> {
            assertThat(summary.key()).isEqualTo("skygridng.api.summary");
            assertThat(summary.arguments()).hasSize(3); // algorithm, tile count, sky spacing
        });
    }
}
