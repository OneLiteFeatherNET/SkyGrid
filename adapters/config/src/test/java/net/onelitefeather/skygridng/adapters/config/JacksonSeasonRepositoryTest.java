package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonSettings;
import net.onelitefeather.skygridng.ports.out.SeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the Jackson season adapter against the {@link SeasonRepository} contract: one
 * season per file under {@code seasons/}, the format chosen by the extension (JSON, YAML or TOML), the
 * file's id is authoritative, unsupported files are ignored, and the optional {@code season} block of
 * {@code config} overrides the defaults.
 */
class JacksonSeasonRepositoryTest {

    private static final Instant START = Instant.parse("2026-12-01T18:00:00Z");

    private static final String JSON = """
            {
              "id": "s01_overgrowth",
              "displayName": "Season 1: Overgrowth",
              "start": "2026-12-01T18:00:00Z",
              "duration": "P8W",
              "preset": "standard",
              "challenges": ["one_life", "no_crafting_table"],
              "seed": 123456789
            }
            """;

    private static final String YAML = """
            id: s01_overgrowth
            displayName: "Season 1: Overgrowth"
            start: 2026-12-01T18:00:00Z
            duration: P8W
            preset: standard
            challenges: [one_life, no_crafting_table]
            seed: 123456789
            """;

    private static final String TOML = """
            id = "s01_overgrowth"
            displayName = "Season 1: Overgrowth"
            start = 2026-12-01T18:00:00Z
            duration = "P8W"
            preset = "standard"
            challenges = ["one_life", "no_crafting_table"]
            seed = 123456789
            """;

    private static SeasonRepository repository(Path dir) {
        return new JacksonSeasonRepository(dir);
    }

    private static void writeSeason(Path dir, String fileName, String content) throws IOException {
        Path seasons = dir.resolve("seasons");
        Files.createDirectories(seasons);
        Files.write(seasons.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeConfig(Path dir, String content) throws IOException {
        Files.write(dir.resolve("config.yml"), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertSeasonS01(Season season) {
        assertThat(season.id()).isEqualTo(new SeasonId("s01_overgrowth"));
        assertThat(season.displayName()).isEqualTo("Season 1: Overgrowth");
        assertThat(season.start()).isEqualTo(START);
        assertThat(season.duration()).isEqualTo(Duration.ofDays(56));
        assertThat(season.preset()).isEqualTo("standard");
        assertThat(season.challenges()).containsExactly("one_life", "no_crafting_table");
        assertThat(season.seed()).isEqualTo(123456789L);
    }

    @Test
    @DisplayName("a JSON season file is loaded with every field, including a week based duration")
    void jsonSeasonIsLoaded(@TempDir Path dir) throws IOException {
        writeSeason(dir, "s01_overgrowth.json", JSON);

        SeasonCatalog catalog = repository(dir).load();

        assertThat(catalog.seasons()).hasSize(1);
        assertSeasonS01(catalog.seasons().getFirst());
    }

    @Test
    @DisplayName("a YAML season file is loaded with every field")
    void yamlSeasonIsLoaded(@TempDir Path dir) throws IOException {
        writeSeason(dir, "s01_overgrowth.yml", YAML);

        SeasonCatalog catalog = repository(dir).load();

        assertThat(catalog.seasons()).hasSize(1);
        assertSeasonS01(catalog.seasons().getFirst());
    }

    @Test
    @DisplayName("a TOML season file is loaded with every field")
    void tomlSeasonIsLoaded(@TempDir Path dir) throws IOException {
        writeSeason(dir, "s01_overgrowth.toml", TOML);

        SeasonCatalog catalog = repository(dir).load();

        assertThat(catalog.seasons()).hasSize(1);
        assertSeasonS01(catalog.seasons().getFirst());
    }

    @Test
    @DisplayName("the id inside the file wins over the file name")
    void fileIdIsAuthoritative(@TempDir Path dir) throws IOException {
        writeSeason(dir, "s01.yml", YAML);

        SeasonCatalog catalog = repository(dir).load();

        assertThat(catalog.seasons()).extracting(s -> s.id().value()).containsExactly("s01_overgrowth");
    }

    @Test
    @DisplayName("the disabled example file is not loaded as a season")
    void disabledExampleIsNotLoaded(@TempDir Path dir) throws IOException {
        writeSeason(dir, "example.yml.disabled", YAML);

        assertThat(repository(dir).load().seasons()).isEmpty();
    }

    @Test
    @DisplayName("an empty seasons directory stays empty after a load")
    void emptySeasonsDirectoryStaysEmpty(@TempDir Path dir) throws IOException {
        Path seasons = Files.createDirectories(dir.resolve("seasons"));

        SeasonCatalog catalog = repository(dir).load();

        assertThat(catalog.seasons()).isEmpty();
        try (var files = Files.list(seasons)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    @DisplayName("an absent seasons directory loads no seasons and is not created")
    void absentSeasonsDirectoryLoadsNoSeasons(@TempDir Path dir) {
        SeasonCatalog catalog = repository(dir).load();

        assertThat(catalog.seasons()).isEmpty();
        assertThat(Files.exists(dir.resolve("seasons"))).isFalse();
    }

    @Test
    @DisplayName("without a config season block the settings are the defaults")
    void missingSeasonBlockYieldsDefaults(@TempDir Path dir) throws IOException {
        writeConfig(dir, "active: classic\n");

        assertThat(repository(dir).settings()).isEqualTo(SeasonSettings.defaults());
    }

    @Test
    @DisplayName("a config season block overrides every setting it names")
    void seasonBlockOverridesSettings(@TempDir Path dir) throws IOException {
        writeConfig(dir, """
                active: classic
                season:
                  prepareLead: PT5M
                  archive:
                    directory: /srv/archive
                    compress: true
                  advance:
                    minNotice: PT48H
                """);

        SeasonSettings settings = repository(dir).settings();

        assertThat(settings.prepareLead()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.archiveDirectory()).isEqualTo("/srv/archive");
        assertThat(settings.archiveCompress()).isTrue();
        assertThat(settings.advanceMinNotice()).isEqualTo(Duration.ofHours(48));
    }

    @Test
    @DisplayName("settings keys left out of the season block keep their defaults")
    void unnamedSettingsKeepDefaults(@TempDir Path dir) throws IOException {
        writeConfig(dir, """
                active: classic
                season:
                  prepareLead: PT5M
                """);

        SeasonSettings settings = repository(dir).settings();

        assertThat(settings.prepareLead()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.archiveDirectory()).isEqualTo(SeasonSettings.defaults().archiveDirectory());
        assertThat(settings.archiveCompress()).isFalse();
        assertThat(settings.advanceMinNotice()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void absentSeasonsDirectoryIsSeededWithTheDisabledExampleThatIsNotLoaded(@TempDir Path dir) {
        byte[] example = "id = \"s01_overgrowth\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        SeasonRepository seeded = new JacksonSeasonRepository(dir,
                path -> path.equals("seasons/example.yml.disabled") ? new java.io.ByteArrayInputStream(example) : null);

        assertThat(seeded.load().seasons())
                .as("the example is written but never loaded, so no season is active by default")
                .isEmpty();
        assertThat(dir.resolve("seasons/example.yml.disabled"))
                .as("an absent seasons directory is seeded with the disabled example")
                .exists();
    }
}
