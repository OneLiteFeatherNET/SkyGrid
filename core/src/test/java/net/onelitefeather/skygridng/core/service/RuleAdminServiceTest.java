package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
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
        DefaultTerrainAlgorithms algorithms = new DefaultTerrainAlgorithms(state, Fixtures.defaultAlgorithmFactories());
        admin = new RuleAdminService(state, presets, algorithms);
    }

    @Test
    @DisplayName("algorithm reads and switches, rejecting unknown ids")
    void algorithmSwitching() {
        assertThat(admin.algorithm()).isEqualTo("skygrid");
        assertThat(admin.setAlgorithm("noise_skygrid")).isTrue();
        assertThat(admin.algorithm()).isEqualTo("noise_skygrid");
        assertThat(admin.setAlgorithm("bogus")).isFalse();
    }

    @Test
    @DisplayName("algorithmIds exposes exactly the registered algorithm ids")
    void algorithmIdsReflectRegistry() {
        assertThat(admin.algorithmIds()).containsExactlyInAnyOrder("skygrid", "noise_skygrid");
    }

    @Test
    @DisplayName("setSkyGridWeight succeeds for an existing or a new material, and rejects bad input")
    void setSkyGridWeight() {
        assertThat(admin.setSkyGridWeight("minecraft:stone", 9.0)).isTrue();   // existing material
        assertThat(admin.setSkyGridWeight("minecraft:diamond_block", 9.0)).isTrue(); // new material
        assertThat(admin.setSkyGridWeight("minecraft:stone", 0.0)).isFalse(); // non positive weight
        assertThat(admin.setSkyGridWeight("bad name!", 1.0)).isFalse(); // invalid block id
    }

    @Test
    @DisplayName("save flattens edits into the active preset and undo rolls back the last change")
    void saveAndUndo() {
        admin.setAlgorithm("noise_skygrid");
        admin.save();
        assertThat(PresetResolver.resolve(repository.load(), "classic").algorithm()).isEqualTo("noise_skygrid");

        assertThat(admin.undo()).isTrue();
        assertThat(admin.algorithm()).isEqualTo("skygrid");
    }
}
