package net.onelitefeather.skygridng.generator;

import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * White-box tests for the compact constructor guards on the value records. These guard the core
 * against malformed configuration before it ever reaches the generator.
 */
class RecordValidationTest {

    private static BlockId mc(String value) {
        return BlockId.of("minecraft:" + value);
    }

    @Test
    @DisplayName("BlockWeight rejects a null material and non positive weight")
    void blockWeight() {
        assertThatThrownBy(() -> new BlockWeight(null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockWeight(mc("stone"), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("BlockId requires a non blank, valid, namespaced id and defaults the namespace")
    void blockId() {
        assertThatThrownBy(() -> BlockId.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlockId.of("bad name!")).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> BlockId.of("mymod:thing"));
        org.assertj.core.api.Assertions.assertThat(BlockId.of("stone").asString()).isEqualTo("minecraft:stone");
    }

    @Test
    @DisplayName("BiomeId requires a non blank, valid, namespaced id and defaults the namespace")
    void biomeId() {
        assertThatThrownBy(() -> BiomeId.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BiomeId.of("bad name!")).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> BiomeId.of("mymod:custom_biome"));
        org.assertj.core.api.Assertions.assertThat(BiomeId.of("plains").asString()).isEqualTo("minecraft:plains");
    }

    @Test
    @DisplayName("Plant rejects bad heights and out of range probability")
    void plant() {
        assertThatThrownBy(() -> new Plant(mc("cactus"), false, null, 0.5, 0, 3, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plant(mc("cactus"), false, null, 0.5, 3, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plant(mc("cactus"), false, null, 1.5, 1, 3, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SkyGridConfig rejects bad spacing, inverted Y, bad noise scale and empty blocks")
    void skyGridConfig() {
        java.util.List<BlockWeight> ok = java.util.List.of(new BlockWeight(mc("stone"), 1));
        assertThatThrownBy(() -> new SkyGridConfig(0, 0, 64, 0.03, ok, java.util.List.of(), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkyGridConfig(4, 10, 0, 0.03, ok, java.util.List.of(), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkyGridConfig(4, 0, 64, 0.0, ok, java.util.List.of(), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkyGridConfig(4, 0, 64, 0.03, java.util.List.of(), java.util.List.of(), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("GeneratorConfig rejects a blank algorithm and a null skygrid section")
    void generatorConfig() {
        assertThatThrownBy(() -> new GeneratorConfig(" ", Fixtures.skyConfig()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeneratorConfig("skygrid", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeneratorConfig("skygrid", Fixtures.skyConfig(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeneratorConfig("skygrid", Fixtures.skyConfig(), SpawnConfig.defaults(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
