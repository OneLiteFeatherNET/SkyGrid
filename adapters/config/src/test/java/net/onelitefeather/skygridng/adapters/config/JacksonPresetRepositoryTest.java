package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.api.model.Preset;
import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.ports.out.PresetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the Jackson preset adapter against the {@link PresetRepository} contract: it
 * seeds the bundled default split layout ({@code config.yml} plus one file per preset), reads the
 * catalog with inheritance overrides left raw for the core to resolve, round-trips a saved catalog,
 * and proves a preset written as JSON or TOML maps to the same {@link Preset} as its YAML twin.
 * Inheritance resolution itself is a core concern, tested there.
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
    @DisplayName("the bundled default seeds the classic/modern/nether catalog")
    void seedsAndLoadsPresets(@TempDir Path dir) {
        PresetCatalog catalog = repository(dir).load();

        assertThat(Files.exists(dir.resolve("config.yml"))).isTrue();
        assertThat(Files.exists(dir.resolve("presets/classic.yml"))).isTrue();
        assertThat(Files.exists(dir.resolve("presets/modern.yml"))).isTrue();
        assertThat(Files.exists(dir.resolve("presets/nether.yml"))).isTrue();

        assertThat(catalog.active()).isEqualTo("classic");
        assertThat(catalog.names()).contains("classic", "modern", "nether");

        Preset classic = catalog.presets().get("classic");
        assertThat(classic.parents()).isEmpty();
        assertThat(classic.blocks()).isNotEmpty();
        assertThat(classic.wfc()).isNotNull();
        assertThat(classic.spawn()).isNotNull();

        Preset modern = catalog.presets().get("modern");
        assertThat(modern.parents()).containsExactly("classic");      // extends
        assertThat(modern.algorithm()).isEqualTo("noise_skygrid");
        assertThat(modern.blocks()).isNotEmpty();                     // overridden
        assertThat(modern.biomes()).isNull();                         // inherited
        assertThat(modern.wfc()).isNull();                            // inherited

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
        assertThat(reloaded.names()).contains("classic", "modern", "nether");
        assertThat(reloaded.presets().get("modern").algorithm()).isEqualTo("noise_skygrid");
    }

    @Test
    @DisplayName("a preset written as JSON or TOML maps to the same Preset as its YAML twin")
    void multipleFormatsAgree(@TempDir Path dir) throws IOException {
        PresetRepository repository = repository(dir);
        PresetCatalog catalog = repository.load();     // seeds and reads the YAML defaults

        // Force modern to JSON and nether to TOML by swapping the file extension, then let save write
        // each preset back in the format of the file that already exists for it.
        Path presets = dir.resolve("presets");
        Files.delete(presets.resolve("modern.yml"));
        Files.createFile(presets.resolve("modern.json"));
        Files.delete(presets.resolve("nether.yml"));
        Files.createFile(presets.resolve("nether.toml"));

        repository(dir).save(catalog);

        assertThat(Files.exists(presets.resolve("modern.json"))).isTrue();
        assertThat(Files.exists(presets.resolve("nether.toml"))).isTrue();
        assertThat(Files.exists(presets.resolve("classic.yml"))).isTrue();

        PresetCatalog reloaded = repository(dir).load();
        assertThat(reloaded.active()).isEqualTo("classic");
        assertThat(reloaded.presets().get("modern")).isEqualTo(catalog.presets().get("modern"));
        assertThat(reloaded.presets().get("nether")).isEqualTo(catalog.presets().get("nether"));
        assertThat(reloaded).isEqualTo(catalog);
    }
}
