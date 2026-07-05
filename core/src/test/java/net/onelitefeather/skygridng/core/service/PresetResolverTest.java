package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.BiomeId;
import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.Preset;
import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the inheritance resolution that powers presets: a child overrides only what it sets and
 * inherits the rest from its base, missing essentials are rejected, and cycles are caught.
 */
class PresetResolverTest {

    private final Preset classic = PresetResolver.toPreset("classic", Fixtures.config("skygrid"));

    private static PresetCatalog catalog(String active, Preset... presets) {
        Map<String, Preset> map = new LinkedHashMap<>();
        for (Preset preset : presets) {
            map.put(preset.name(), preset);
        }
        return new PresetCatalog(map, active);
    }

    /** A preset overriding only the fields named; everything else is null (inherited). */
    private Preset child(String name, String parent, String algorithm, List<BlockWeight> blocks, List<BiomeId> biomes) {
        List<String> parents = parent == null ? List.of() : List.of(parent);
        return new Preset(name, parents, algorithm, null, null, null, null, blocks, null, null, biomes, null, null);
    }

    @Test
    @DisplayName("a root preset round-trips through toPreset and resolve")
    void rootRoundTrips() {
        PresetCatalog catalog = catalog("classic", classic);
        assertThat(PresetResolver.resolve(catalog, "classic")).isEqualTo(Fixtures.config("skygrid"));
    }

    @Test
    @DisplayName("a child overrides its own fields and inherits the rest from the base")
    void childOverridesAndInherits() {
        List<BlockWeight> standardBlocks = List.of(new BlockWeight(Fixtures.mc("deepslate"), 20.0));
        Preset standard = child("standard", "classic", "noise_skygrid", standardBlocks, null);

        GeneratorConfig resolved = PresetResolver.resolve(catalog("standard", classic, standard), "standard");

        assertThat(resolved.algorithm()).isEqualTo("noise_skygrid");        // overridden
        assertThat(resolved.skygrid().blocks()).isEqualTo(standardBlocks);     // overridden
        assertThat(resolved.wfc()).isEqualTo(Fixtures.config("skygrid").wfc());     // inherited
        assertThat(resolved.spawn()).isEqualTo(Fixtures.config("skygrid").spawn()); // inherited
        assertThat(resolved.skygrid().spacing()).isEqualTo(4);              // inherited scalar
    }

    @Test
    @DisplayName("a child can override the biome palette (nether biomes)")
    void overridesBiomes() {
        List<BiomeId> netherBiomes = List.of(BiomeId.of("minecraft:nether_wastes"), BiomeId.of("minecraft:crimson_forest"));
        Preset nether = child("nether", "classic", null,
                List.of(new BlockWeight(Fixtures.mc("netherrack"), 30.0)), netherBiomes);

        GeneratorConfig resolved = PresetResolver.resolve(catalog("nether", classic, nether), "nether");

        assertThat(resolved.biomes()).isEqualTo(netherBiomes);
        assertThat(resolved.algorithm()).isEqualTo("skygrid"); // inherited
    }

    @Test
    @DisplayName("a preset can extend multiple parents; the later parent wins, over a shared base")
    void multipleParentsMergeLeftToRight() {
        // water and lava both extend classic (diamond); mix extends [water, lava].
        Preset water = new Preset("water", List.of("classic"), null, null, null, null, null,
                List.of(new BlockWeight(Fixtures.mc("water"), 50.0)), null, null, null, null, null);
        Preset lava = new Preset("lava", List.of("classic"), "noise_skygrid", null, null, null, null,
                List.of(new BlockWeight(Fixtures.mc("lava"), 50.0)), null, null, null, null, null);
        Preset mix = new Preset("mix", List.of("water", "lava"), null, null, null, null, null,
                null, null, null, null, null, null);

        GeneratorConfig resolved = PresetResolver.resolve(catalog("mix", classic, water, lava, mix), "mix");

        // lava is the later parent, so its blocks and algorithm win
        assertThat(resolved.skygrid().blocks()).containsExactly(new BlockWeight(Fixtures.mc("lava"), 50.0));
        assertThat(resolved.algorithm()).isEqualTo("noise_skygrid");
        // the shared base (classic) is applied once and supplies wfc
        assertThat(resolved.wfc()).isEqualTo(Fixtures.config("skygrid").wfc());
    }

    @Test
    @DisplayName("a chain with no block palette anywhere is rejected")
    void rejectsMissingBlocks() {
        Preset noBlocks = new Preset("bare", List.of(), "skygrid", 4, 0, 64, 0.03,
                null, null, null, null, null, Fixtures.ruleSet());
        assertThatThrownBy(() -> PresetResolver.resolve(catalog("bare", noBlocks), "bare"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("block palette");
    }

    @Test
    @DisplayName("a chain with no wfc rule set is rejected")
    void rejectsMissingWfc() {
        Preset noWfc = new Preset("bare", List.of(), "skygrid", 4, 0, 64, 0.03,
                List.of(new BlockWeight(Fixtures.mc("stone"), 1.0)), null, null, null, null, null);
        assertThatThrownBy(() -> PresetResolver.resolve(catalog("bare", noWfc), "bare"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wfc");
    }

    @Test
    @DisplayName("an inheritance cycle is rejected")
    void rejectsCycle() {
        Preset a = child("a", "b", null, null, null);
        Preset b = child("b", "a", null, null, null);
        assertThatThrownBy(() -> PresetResolver.resolve(catalog("a", a, b), "a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("extending an unknown preset is rejected")
    void rejectsUnknownParent() {
        Preset orphan = child("orphan", "ghost", null,
                List.of(new BlockWeight(Fixtures.mc("stone"), 1.0)), null);
        assertThatThrownBy(() -> PresetResolver.resolve(catalog("orphan", orphan), "orphan"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
    }
}
