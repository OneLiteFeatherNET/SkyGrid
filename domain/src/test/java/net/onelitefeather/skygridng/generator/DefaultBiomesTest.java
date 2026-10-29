package net.onelitefeather.skygridng.generator;

import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the deterministic biome map: every returned biome is in the declared palette,
 * the map is reproducible and region-stable, and more than one biome appears across space.
 */
class DefaultBiomesTest {

    private final Biomes biomes = new DefaultBiomes(
            new RuleState(Fixtures.config("skygrid"), 4), Set.copyOf(GeneratorConfig.DEFAULT_BIOMES));

    @Test
    @DisplayName("every returned biome is in the palette and several biomes appear across space")
    void biomesAreInPalette() {
        long seed = 123L;
        Set<BiomeId> seen = new HashSet<>();
        for (int x = -500; x <= 500; x += 37) {
            for (int z = -500; z <= 500; z += 41) {
                BiomeId biome = biomes.at(seed, x, 64, z);
                assertThat(biomes.palette()).contains(biome);
                seen.add(biome);
            }
        }
        assertThat(biomes.palette()).isNotEmpty();
        assertThat(seen).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("the biome map is deterministic, region-stable and independent of y")
    void deterministicAndRegional() {
        long seed = 987654321L;
        assertThat(biomes.at(seed, 10, 64, 10)).isEqualTo(biomes.at(seed, 10, 64, 10));
        // coordinates inside the same 96-block region share a biome
        assertThat(biomes.at(seed, 0, 64, 0)).isEqualTo(biomes.at(seed, 5, 64, 5));
        // the map is 2D: y does not change the biome
        assertThat(biomes.at(seed, 10, 0, 10)).isEqualTo(biomes.at(seed, 10, 200, 10));
    }
}
