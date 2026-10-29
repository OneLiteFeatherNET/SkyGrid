package net.onelitefeather.skygridng.config;

import net.onelitefeather.skygridng.generator.Preset;
import net.onelitefeather.skygridng.generator.PresetCatalog;
import net.onelitefeather.skygridng.generator.PresetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the Jackson preset adapter against the {@link PresetRepository} contract: it
 * seeds the bundled default split layout ({@code config.yml} plus one file per preset), reads the
 * catalog with inheritance overrides left raw for the core to resolve, round-trips a saved catalog,
 * accepts the {@code .yaml} extension as well as {@code .yml}, and skips a JSON file in {@code presets/}
 * with a warning. Inheritance resolution itself is a core concern, tested there.
 */
class JacksonPresetRepositoryTest {

    /** Bundled defaults backed by the module's own resources on the test classpath. */
    private BundledDefaults bundled() {
        return path -> getClass().getResourceAsStream("/" + path);
    }

    private PresetRepository repository(Path dir) {
        return new JacksonPresetRepository(dir, bundled());
    }

    @Test
    @DisplayName("the bundled default seeds the classic/standard/nether catalog")
    void seedsAndLoadsPresets(@TempDir Path dir) {
        PresetCatalog catalog = repository(dir).load();

        assertThat(Files.exists(dir.resolve("config.yml"))).isTrue();
        assertThat(Files.exists(dir.resolve("presets/classic.yml"))).isTrue();
        assertThat(Files.exists(dir.resolve("presets/standard.yml"))).isTrue();
        assertThat(Files.exists(dir.resolve("presets/nether.yml"))).isTrue();

        assertThat(catalog.active()).isEqualTo("classic");
        assertThat(catalog.names()).contains("classic", "standard", "nether");

        Preset classic = catalog.presets().get("classic");
        assertThat(classic.parents()).isEmpty();
        assertThat(classic.blocks()).isNotEmpty();
        assertThat(classic.spawn()).isNotNull();

        Preset standard = catalog.presets().get("standard");
        assertThat(standard.parents()).containsExactly("classic");      // extends
        assertThat(standard.algorithm()).isEqualTo("noise_skygrid");
        assertThat(standard.blocks()).isNotEmpty();                     // overridden
        assertThat(standard.biomes()).isNull();                         // inherited

        Preset nether = catalog.presets().get("nether");
        assertThat(nether.parents()).containsExactly("classic");
        assertThat(nether.biomes()).isNotEmpty();                     // nether biomes
    }

    @Test
    @DisplayName("a saved catalog is read back with the new active preset")
    void roundTrip(@TempDir Path dir) {
        PresetRepository repository = repository(dir);

        PresetCatalog original = repository.load();
        repository.save(original.withActive("nether"));

        PresetCatalog reloaded = repository(dir).load();
        assertThat(reloaded.active()).isEqualTo("nether");
        assertThat(reloaded.names()).contains("classic", "standard", "nether");
        assertThat(reloaded.presets().get("standard").algorithm()).isEqualTo("noise_skygrid");
    }

    @Test
    @DisplayName("a preset file with the .yaml extension loads like its .yml twin")
    void yamlExtensionLoadsLikeYml(@TempDir Path dir) throws IOException {
        PresetCatalog original = repository(dir).load();
        Path presets = dir.resolve("presets");
        Files.move(presets.resolve("standard.yml"), presets.resolve("standard.yaml"));

        PresetCatalog reloaded = repository(dir).load();

        assertThat(reloaded).isEqualTo(original);
    }

    @Test
    @DisplayName("a JSON preset file is skipped with one warning and the YAML presets still load")
    void jsonPresetIsSkippedWithWarning(@TempDir Path dir) throws IOException {
        PresetCatalog original = repository(dir).load();
        Files.writeString(dir.resolve("presets/legacy.json"), "{\"algorithm\": \"skygrid\"}");

        try (ConfigLogCapture log = new ConfigLogCapture()) {
            PresetCatalog reloaded = repository(dir).load();

            assertThat(reloaded.names()).doesNotContain("legacy");
            assertThat(reloaded).isEqualTo(original);
            assertThat(log.warnings()).as("one warning for the skipped JSON file").hasSize(1);
            assertThat(log.warnings().getFirst())
                    .contains("legacy.json")
                    .contains("no longer supported");
        }
    }

    @Test
    @DisplayName("saving the presets keeps the season block of config")
    void savingPresetsKeepsSeasonBlock(@TempDir Path dir) throws IOException {
        PresetRepository repository = repository(dir);
        PresetCatalog catalog = repository.load();
        Files.writeString(dir.resolve("config.yml"), """
                active: classic
                season:
                  prepareLead: PT5M
                """);

        repository.save(catalog);

        assertThat(new JacksonSeasonRepository(dir).settings().prepareLead()).isEqualTo(Duration.ofMinutes(5));
    }
}
