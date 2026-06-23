package net.onelitefeather.skygridng.adapter.out.json;

import net.onelitefeather.skygridng.core.model.GeneratorConfig;
import net.onelitefeather.skygridng.core.port.out.RuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the JSON out adapter against the {@link RuleRepository} contract: it creates
 * the file from the bundled default and round-trips a saved configuration.
 */
class JsonRuleRepositoryTest {

    private String defaultJson() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/generator.json")) {
            assertThat(in).as("bundled generator.json on the test classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("load creates the file from the default when it is missing")
    void loadCreatesDefault(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("generator.json");
        RuleRepository repository = new JsonRuleRepository(file, defaultJson());

        GeneratorConfig config = repository.load();

        assertThat(Files.exists(file)).isTrue();
        assertThat(config.algorithm()).isEqualTo("skygrid");
        assertThat(config.skygrid().spacing()).isEqualTo(4);
        assertThat(config.wfc().tiles()).isNotEmpty();
    }

    @Test
    @DisplayName("a saved configuration is read back identically")
    void roundTrip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("generator.json");
        RuleRepository repository = new JsonRuleRepository(file, defaultJson());

        GeneratorConfig original = repository.load();
        GeneratorConfig changed = original
                .withAlgorithm("wfc")
                .withSkygrid(original.skygrid().withBlockWeight("DIAMOND_BLOCK", 1.0));
        repository.save(changed);

        // A fresh repository over the same file must observe the persisted state.
        GeneratorConfig reloaded = new JsonRuleRepository(file, defaultJson()).load();
        assertThat(reloaded.algorithm()).isEqualTo("wfc");
        assertThat(reloaded.skygrid().blocks())
                .anyMatch(b -> b.material().equals("DIAMOND_BLOCK"));
        assertThat(reloaded.wfc().tileIds()).isEqualTo(original.wfc().tileIds());
    }
}
