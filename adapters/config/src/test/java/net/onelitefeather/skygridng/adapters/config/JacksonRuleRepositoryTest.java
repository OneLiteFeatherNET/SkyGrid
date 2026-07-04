package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.PhysicsMode;
import net.onelitefeather.skygridng.api.model.Rule;
import net.onelitefeather.skygridng.api.model.RuleCatalog;
import net.onelitefeather.skygridng.api.model.WaterMode;
import net.onelitefeather.skygridng.ports.out.RuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the Jackson rule adapter against the {@link RuleRepository} contract: it seeds
 * the bundled default {@code rules/} directory (one file per block), reads a per-block rule with its
 * state, physics and optional placement, round-trips a saved catalog, and proves a rule written as
 * TOML maps to the same {@link Rule} as its YAML twin.
 */
class JacksonRuleRepositoryTest {

    private BundledDefaults bundled() {
        return path -> getClass().getResourceAsStream("/" + path);
    }

    private RuleRepository repository(Path dir) {
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
        assertThat(cane.placement().requiresGround()).isEqualTo(BlockId.of("minecraft:sand"));
        assertThat(cane.placement().water()).isEqualTo(WaterMode.SOURCE);
        assertThat(cane.placement().minHeight()).isEqualTo(1);
        assertThat(cane.placement().maxHeight()).isEqualTo(3);
        assertThat(catalog.decorations()).extracting(Rule::block).contains(BlockId.of("minecraft:sugar_cane"));
    }

    @Test
    @DisplayName("a saved catalog is read back unchanged")
    void roundTrip(@TempDir Path dir) {
        RuleRepository repository = repository(dir);
        RuleCatalog original = repository.load();

        repository.save(original);

        RuleCatalog reloaded = repository(dir).load();
        assertThat(reloaded).isEqualTo(original);
    }

    @Test
    @DisplayName("a rule written as TOML maps to the same Rule as its YAML twin")
    void tomlAgreesWithYaml(@TempDir Path dir) throws IOException {
        RuleRepository repository = repository(dir);
        RuleCatalog catalog = repository.load();

        // Force sugar_cane to TOML, then let save write it back in that format.
        Path rules = dir.resolve("rules");
        Files.delete(rules.resolve("sugar_cane.yml"));
        Files.createFile(rules.resolve("sugar_cane.toml"));

        repository(dir).save(catalog);
        assertThat(Files.exists(rules.resolve("sugar_cane.toml"))).isTrue();

        RuleCatalog reloaded = repository(dir).load();
        assertThat(reloaded.forBlock(BlockId.of("minecraft:sugar_cane")))
                .isEqualTo(catalog.forBlock(BlockId.of("minecraft:sugar_cane")));
    }
}
