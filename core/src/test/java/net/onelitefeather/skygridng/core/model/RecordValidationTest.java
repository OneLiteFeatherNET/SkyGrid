package net.onelitefeather.skygridng.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    @DisplayName("TileType rejects blank id and non positive weight")
    void tileType() {
        assertThatThrownBy(() -> new TileType(" ", 1, true, mc("stone"), mc("stone"), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TileType("x", 0, true, mc("stone"), mc("stone"), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new TileType("x", 1, true, mc("stone"), mc("stone"), false, null));
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
    @DisplayName("AllowedPair rejects blank endpoints")
    void allowedPair() {
        assertThatThrownBy(() -> new AllowedPair("a", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AllowedPair(null, "b")).isInstanceOf(IllegalArgumentException.class);
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
    @DisplayName("LootChest and Spawner reject null ids and out of range probability")
    void lootChestAndSpawner() {
        assertThatThrownBy(() -> new LootChest(null, mc("chests/x"), 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LootChest(mc("chest"), mc("chests/x"), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Spawner(mc("spawner"), null, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new Spawner(mc("spawner"), mc("zombie"), 0.1));
    }

    @Test
    @DisplayName("GenerationSettings rejects degenerate sizes and a blank fallback")
    void generationSettings() {
        assertThatThrownBy(() -> new GenerationSettings(0, 8, 64, 8, "x", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationSettings(4, 1, 64, 8, "x", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationSettings(4, 8, 64, 0, "x", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationSettings(4, 8, 64, 8, " ", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SkyGridConfig rejects bad spacing, inverted Y, bad noise scale and empty blocks")
    void skyGridConfig() {
        List<BlockWeight> ok = List.of(new BlockWeight(mc("stone"), 1));
        assertThatThrownBy(() -> new SkyGridConfig(0, 0, 64, 0.03, ok, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkyGridConfig(4, 10, 0, 0.03, ok, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkyGridConfig(4, 0, 64, 0.0, ok, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkyGridConfig(4, 0, 64, 0.03, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("GeneratorConfig rejects a blank algorithm and null sections")
    void generatorConfig() {
        assertThatThrownBy(() -> new GeneratorConfig(" ",
                net.onelitefeather.skygridng.testutil.Fixtures.ruleSet(),
                net.onelitefeather.skygridng.testutil.Fixtures.skyConfig()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeneratorConfig("skygrid", null,
                net.onelitefeather.skygridng.testutil.Fixtures.skyConfig()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeneratorConfig("skygrid",
                net.onelitefeather.skygridng.testutil.Fixtures.ruleSet(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
