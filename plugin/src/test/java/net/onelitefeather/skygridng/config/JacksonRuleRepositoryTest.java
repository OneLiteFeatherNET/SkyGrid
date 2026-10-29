package net.onelitefeather.skygridng.config;

import net.onelitefeather.skygridng.generator.BlockId;
import net.onelitefeather.skygridng.generator.LegendEntry;
import net.onelitefeather.skygridng.generator.PhysicsMode;
import net.onelitefeather.skygridng.generator.PlacementRule;
import net.onelitefeather.skygridng.generator.Rule;
import net.onelitefeather.skygridng.generator.RuleCatalog;
import net.onelitefeather.skygridng.generator.StructurePattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Black-box tests for the Jackson rule adapter against the {@link JacksonRuleRepository} adapter: it seeds
 * the bundled default {@code rules/} directory (one file per block), reads a per-block rule with its
 * state, physics and optional placement, and round-trips a saved catalog.
 */
class JacksonRuleRepositoryTest {

    private BundledDefaults bundled() {
        return path -> getClass().getResourceAsStream("/" + path);
    }

    private JacksonRuleRepository repository(Path dir) {
        return new JacksonRuleRepository(dir, bundled());
    }

    @Test
    @DisplayName("the bundled default seeds the per-block rules")
    void seedsAndLoadsRules(@TempDir Path dir) {
        RuleCatalog catalog = repository(dir).load();

        assertThat(Files.exists(dir.resolve("rules/oak_leaves.yml"))).isTrue();
        assertThat(Files.exists(dir.resolve("rules/sugar_cane.yml"))).isTrue();

        Rule leaves = catalog.forBlock(BlockId.of("minecraft:oak_leaves")).orElseThrow();
        assertThat(leaves.state()).containsEntry("persistent", "true");
        assertThat(leaves.physics()).isEqualTo(PhysicsMode.NONE);
        assertThat(leaves.isDecoration()).isFalse();

        Rule water = catalog.forBlock(BlockId.of("minecraft:water")).orElseThrow();
        assertThat(water.state()).containsEntry("level", "0");

        Rule cane = catalog.forBlock(BlockId.of("minecraft:sugar_cane")).orElseThrow();
        assertThat(cane.isDecoration()).isTrue();
        assertThat(cane.placement().minHeight()).isEqualTo(1);
        assertThat(cane.placement().maxHeight()).isEqualTo(3);
        assertThat(cane.placement().probability()).isEqualTo(0.04);
        assertThat(catalog.decorations()).extracting(Rule::block).contains(BlockId.of("minecraft:sugar_cane"));

        // The shipped structure fixes the falling-sand bug: a solid sandstone floor (never a gravity
        // block) under the whole footprint, with the sand support boxed in by more sandstone and a
        // contained water source, so the sand is never left unsupported at generation time.
        StructurePattern structure = cane.placement().structure();
        assertThat(structure.filler()).isEqualTo(BlockId.of("minecraft:sandstone"));
        assertThat(structure.layers()).containsExactly(
                List.of("###", "###", "###"),
                List.of(" # ", "SW#", " # "));
        assertThat(structure.legend().get('S'))
                .isEqualTo(new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true));
        assertThat(structure.legend().get('W'))
                .isEqualTo(new LegendEntry(BlockId.of("minecraft:water"), Map.of(), false));
        // Verified empirically against the real StructurePattern: the anchor 'S' sits at layer 1
        // (the ground layer), row 1, col 0.
        assertThat(structure.anchorOffset()).containsExactly(1, 1, 0);
    }

    @Test
    @DisplayName("the bundled default seeds the golden_dandelion decoration rule")
    void seedsAndLoadsGoldenDandelionRule(@TempDir Path dir) {
        RuleCatalog catalog = repository(dir).load();

        assertThat(Files.exists(dir.resolve("rules/golden_dandelion.yml"))).isTrue();

        Rule dandelion = catalog.forBlock(BlockId.of("minecraft:golden_dandelion")).orElseThrow();
        assertThat(dandelion.isDecoration()).isTrue();
        assertThat(dandelion.physics()).isEqualTo(PhysicsMode.NONE);
        assertThat(dandelion.placement().minHeight()).isEqualTo(1);
        assertThat(dandelion.placement().maxHeight()).isEqualTo(1);
        assertThat(dandelion.placement().probability()).isEqualTo(0.03);
        assertThat(catalog.decorations()).extracting(Rule::block).contains(BlockId.of("minecraft:golden_dandelion"));

        StructurePattern structure = dandelion.placement().structure();
        assertThat(structure.filler()).isEqualTo(BlockId.of("minecraft:dirt"));
        assertThat(structure.layers()).containsExactly(List.of("G"));
        assertThat(structure.legend().get('G'))
                .isEqualTo(new LegendEntry(BlockId.of("minecraft:grass_block"), Map.of(), true));
    }

    @Test
    @DisplayName("a saved catalog is read back unchanged")
    void roundTrip(@TempDir Path dir) {
        JacksonRuleRepository repository = repository(dir);
        RuleCatalog original = repository.load();

        repository.save(original);

        RuleCatalog reloaded = repository(dir).load();
        assertThat(reloaded).isEqualTo(original);
    }

    // ----- multi-layer structure round trip -----

    private static final PlacementRule EXPECTED_PLACEMENT = new PlacementRule(
            new StructurePattern(
                    List.of(List.of("###", "###"), List.of("AB#", " # ")),
                    Map.of(
                            'A', new LegendEntry(BlockId.of("minecraft:dirt"), Map.of(), true),
                            'B', new LegendEntry(BlockId.of("minecraft:water"), Map.of("level", "0"), false)),
                    BlockId.of("minecraft:stone")),
            2, 4, 0.1);

    private void assertLoadsExpectedPlacement(Path dir) {
        Rule rule = repository(dir).load().forBlock(BlockId.of("minecraft:test_plant")).orElseThrow();

        assertThat(rule.physics()).isEqualTo(PhysicsMode.NONE);
        assertThat(rule.placement()).isEqualTo(EXPECTED_PLACEMENT);
        assertThat(rule.placement().structure().layers())
                .containsExactly(List.of("###", "###"), List.of("AB#", " # "));
        assertThat(rule.placement().structure().legend().get('A'))
                .isEqualTo(new LegendEntry(BlockId.of("minecraft:dirt"), Map.of(), true));
        assertThat(rule.placement().structure().legend().get('B'))
                .isEqualTo(new LegendEntry(BlockId.of("minecraft:water"), Map.of("level", "0"), false));
        assertThat(rule.placement().structure().filler()).isEqualTo(BlockId.of("minecraft:stone"));
    }

    private void writeRule(Path dir, String fileName, String content) throws IOException {
        Path rules = dir.resolve("rules");
        Files.createDirectories(rules);
        Files.write(rules.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a multi-layer structure round trips through YAML")
    void multiLayerStructureYaml(@TempDir Path dir) throws IOException {
        writeRule(dir, "test_plant.yml", """
                block: minecraft:test_plant
                physics: none
                placement:
                  minHeight: 2
                  maxHeight: 4
                  probability: 0.1
                  structure:
                    filler: minecraft:stone
                    legend:
                      A: { block: minecraft:dirt, anchor: true }
                      B: { block: minecraft:water, state: { level: "0" } }
                    layers:
                      - ["###", "###"]
                      - ["AB#", " # "]
                """);

        assertLoadsExpectedPlacement(dir);
    }

    // ----- fail-fast validation, naming the offending file -----

    @Test
    @DisplayName("a structure legend key that is not exactly one character fails fast, naming the file")
    void legendKeyNotOneCharacterFailsFast(@TempDir Path dir) throws IOException {
        writeRule(dir, "test_plant.yml", """
                block: minecraft:test_plant
                physics: none
                placement:
                  minHeight: 1
                  maxHeight: 1
                  probability: 1.0
                  structure:
                    filler: minecraft:stone
                    legend:
                      AB: { block: minecraft:dirt, anchor: true }
                    layers:
                      - ["AB"]
                """);

        assertThatThrownBy(() -> repository(dir).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_plant.yml")
                .hasMessageContaining("invalid structure legend key 'AB'")
                .hasMessageContaining("must be exactly one character");
    }

    @Test
    @DisplayName("a structure legend entry that reuses a reserved symbol fails fast, naming the file")
    void legendReservedSymbolCollisionFailsFast(@TempDir Path dir) throws IOException {
        writeRule(dir, "test_plant.yml", """
                block: minecraft:test_plant
                physics: none
                placement:
                  minHeight: 1
                  maxHeight: 1
                  probability: 1.0
                  structure:
                    filler: minecraft:stone
                    legend:
                      '#': { block: minecraft:dirt, anchor: true }
                    layers:
                      - ["#"]
                """);

        assertThatThrownBy(() -> repository(dir).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_plant.yml")
                .hasMessageContaining("structure legend entry '#'")
                .hasMessageContaining("reuses a reserved symbol");
    }

    @Test
    @DisplayName("a placement block missing its structure fails fast, naming the file")
    void placementMissingStructureFailsFast(@TempDir Path dir) throws IOException {
        writeRule(dir, "test_plant.yml", """
                block: minecraft:test_plant
                physics: none
                placement:
                  minHeight: 1
                  maxHeight: 1
                  probability: 1.0
                """);

        assertThatThrownBy(() -> repository(dir).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_plant.yml")
                .hasMessageContaining("has a 'placement' block but is missing 'structure'");
    }

    @Test
    @DisplayName("an invalid structure pattern (unknown grid symbol) fails fast, naming the file")
    void invalidStructurePatternFailsFast(@TempDir Path dir) throws IOException {
        writeRule(dir, "test_plant.yml", """
                block: minecraft:test_plant
                physics: none
                placement:
                  minHeight: 1
                  maxHeight: 1
                  probability: 1.0
                  structure:
                    filler: minecraft:stone
                    legend:
                      A: { block: minecraft:dirt, anchor: true }
                    layers:
                      - ["AX"]
                """);

        assertThatThrownBy(() -> repository(dir).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_plant.yml")
                .hasMessageContaining("invalid structure pattern")
                .hasMessageContaining("unknown symbol 'X'");
    }
}
