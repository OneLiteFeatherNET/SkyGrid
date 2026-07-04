package net.onelitefeather.skygridng.core.service;

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
 * Black-box tests for the admin use case. A single-preset in memory catalog stands in for the
 * Configurate adapter, keeping the test on the port contract. Saving flattens the edits back into
 * the active preset.
 */
class RuleAdminServiceTest {

    private Fixtures.InMemoryPresetRepository repository;
    private RuleAdminUseCase admin;

    @BeforeEach
    void setUp() {
        PresetCatalog catalog = Fixtures.presetCatalog("skygrid");
        repository = new Fixtures.InMemoryPresetRepository(catalog);
        RuleState state = new RuleState(PresetResolver.resolve(catalog, catalog.active()), 8);
        PresetService presets = new PresetService(repository, state, catalog);
        RegionGenerationService region = new RegionGenerationService(state,
                new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
        DefaultTerrainAlgorithms algorithms = new DefaultTerrainAlgorithms(state,
                Fixtures.defaultAlgorithmFactories(), region);
        admin = new RuleAdminService(state, presets, algorithms);
    }

    @Test
    @DisplayName("algorithm reads and switches, rejecting unknown ids")
    void algorithmSwitching() {
        assertThat(admin.algorithm()).isEqualTo("skygrid");
        assertThat(admin.setAlgorithm("wfc")).isTrue();
        assertThat(admin.algorithm()).isEqualTo("wfc");
        assertThat(admin.setAlgorithm("bogus")).isFalse();
    }

    @Test
    @DisplayName("tile lookups return known tiles and report unknown ones")
    void tileLookups() {
        assertThat(admin.tileIds()).containsExactlyInAnyOrder("water", "sand", "grass", "cactus");
        assertThat(admin.tile("grass")).isPresent();
        assertThat(admin.tile("missing")).isEmpty();
    }

    @Test
    @DisplayName("setWeight succeeds for a known tile and fails for an unknown one")
    void setWeight() {
        assertThat(admin.setWeight("grass", 42.0)).isTrue();
        assertThat(admin.tile("grass")).get().extracting(t -> t.weight()).isEqualTo(42.0);
        assertThat(admin.setWeight("missing", 1.0)).isFalse();
    }

    @Test
    @DisplayName("allow, disallow and skyweight report success")
    void adjacencyAndSkyWeight() {
        assertThat(admin.disallow("water", "sand")).isTrue();
        assertThat(admin.allow("water", "grass")).isTrue();
        assertThat(admin.setSkyGridWeight("minecraft:stone", 9.0)).isTrue();   // existing material
        assertThat(admin.setSkyGridWeight("minecraft:diamond_block", 9.0)).isTrue(); // new material
    }

    @Test
    @DisplayName("save flattens edits into the active preset and undo rolls back the last change")
    void saveAndUndo() {
        admin.setAlgorithm("wfc");
        admin.save();
        assertThat(PresetResolver.resolve(repository.load(), "classic").algorithm()).isEqualTo("wfc");

        assertThat(admin.undo()).isTrue();
        assertThat(admin.algorithm()).isEqualTo("skygrid");
    }
}
