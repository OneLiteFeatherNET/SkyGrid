package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.Preset;
import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the per-world heart of the preset service: each preset has its own live state, an edit to a
 * base preset propagates to the worlds that derive from it, activating a preset changes only the
 * default, and world assignments are tracked.
 */
class PresetServiceTest {

    private RuleState active;
    private PresetService presets;

    @BeforeEach
    void setUp() {
        Preset classic = PresetResolver.toPreset("classic", Fixtures.config("skygrid"));
        // "stone" extends classic and overrides only the block palette.
        Preset stone = new Preset("stone", List.of("classic"), null, null, null, null, null,
                List.of(new BlockWeight(Fixtures.mc("stone"), 1.0)), null, null, null, null, null);
        Map<String, Preset> map = new LinkedHashMap<>();
        map.put("classic", classic);
        map.put("stone", stone);
        PresetCatalog catalog = new PresetCatalog(map, "classic");

        Fixtures.InMemoryPresetRepository repository = new Fixtures.InMemoryPresetRepository(catalog);
        active = new RuleState(PresetResolver.resolve(catalog, "classic"), 8);
        presets = new PresetService(repository, active, catalog);
    }

    @Test
    @DisplayName("stateFor resolves a preset into its own live state")
    void stateForResolvesPreset() {
        RuleState stone = presets.stateFor("stone");
        assertThat(stone.current().skygrid().blocks()).containsExactly(new BlockWeight(Fixtures.mc("stone"), 1.0));
    }

    @Test
    @DisplayName("editing a base preset and saving propagates to a world that derives from it")
    void editPropagatesToDerivedWorld() {
        RuleState worldUsingStone = presets.stateFor("stone"); // stone extends classic
        assertThat(worldUsingStone.current().algorithm()).isEqualTo("skygrid");

        active.apply(active.current().withAlgorithm("noise_skygrid")); // edit the active (classic)
        presets.save();

        assertThat(worldUsingStone.current().algorithm()).isEqualTo("noise_skygrid"); // inherited the edit
        assertThat(worldUsingStone.current().skygrid().blocks())              // its own override survives
                .containsExactly(new BlockWeight(Fixtures.mc("stone"), 1.0));
    }

    @Test
    @DisplayName("activating a preset changes only the default, not a pinned per-preset state")
    void activateChangesDefaultOnly() {
        RuleState stoneState = presets.stateFor("stone");

        assertThat(presets.activate("stone")).isTrue();

        assertThat(presets.active()).isEqualTo("stone");
        assertThat(active.current().skygrid().blocks())                       // default now resolves stone
                .containsExactly(new BlockWeight(Fixtures.mc("stone"), 1.0));
        assertThat(stoneState.current().skygrid().blocks())                   // the pinned state is unchanged
                .containsExactly(new BlockWeight(Fixtures.mc("stone"), 1.0));
    }

    @Test
    @DisplayName("activation matches the preset name case-insensitively")
    void activateIsCaseInsensitive() {
        assertThat(presets.activate("STONE")).isTrue();
        assertThat(presets.active()).isEqualTo("stone");        // resolves to the actual catalog key
        assertThat(presets.activate("does_not_exist")).isFalse();
    }

    @Test
    @DisplayName("world assignments are recorded and reported")
    void recordsWorldAssignments() {
        presets.recordWorld("hub", "classic");
        presets.recordWorld("islands", "stone");
        assertThat(presets.worlds()).containsEntry("hub", "classic").containsEntry("islands", "stone");
    }
}
