package net.onelitefeather.skygridng.generator;

import org.bukkit.block.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the biome resolver. MockBukkit provides the biome registry; a known id resolves to a
 * non-null biome and an unknown id falls back rather than breaking world load.
 */
class BiomeResolverTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a known biome id resolves and an unknown one falls back")
    void resolves() {
        BiomeResolver resolver = new BiomeResolver(Biome.PLAINS);
        assertThat(resolver.resolve(BiomeId.of("minecraft:plains"))).isNotNull();
        assertThat(resolver.resolve(BiomeId.of("minecraft:not_a_real_biome_xyz"))).isEqualTo(Biome.PLAINS);
    }
}
