package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.adapters.config.ConfigDtos.ConfigDto;
import net.onelitefeather.skygridng.adapters.config.ConfigDtos.SeasonSettingsDto;
import net.onelitefeather.skygridng.adapters.config.SeasonDtos.SeasonDto;
import net.onelitefeather.skygridng.api.model.Season;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonSettings;
import net.onelitefeather.skygridng.ports.out.SeasonRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Out adapter: reads the season definitions and the optional {@code season} block of {@code config}. Each
 * season is one file under {@code seasons/} ({@code seasons/s01_overgrowth.yml}), and the format is chosen per
 * file from its extension (JSON, YAML or TOML). The id inside the file is authoritative. Files with any other
 * extension, such as {@code example.yml.disabled}, are not loaded. The adapter only maps the files to
 * {@link Season}; the core validates presets, challenges and overlaps. An absent or empty {@code seasons/}
 * directory yields no seasons, which the core reads as the implicit legacy season.
 */
public final class JacksonSeasonRepository implements SeasonRepository {

    private static final String SEASONS_DIR = "seasons";
    private static final String CONFIG_BASE = "config";
    private static final List<String> CONFIG_EXTENSIONS = List.of("json", "yml", "yaml", "toml");

    private final Path dataFolder;
    private final ConfigSchemas schemas = ConfigSchemas.shared();

    public JacksonSeasonRepository(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    @Override
    public SeasonCatalog load() {
        try {
            schemas.writeTo(dataFolder);

            List<Season> seasons = new ArrayList<>();
            for (Path file : seasonFiles()) {
                SeasonDto dto = ConfigFormats.mapperFor(file).readValue(Files.readAllBytes(file), SeasonDto.class);
                seasons.add(toSeason(dto, file));
            }
            return new SeasonCatalog(seasons);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load seasons in " + dataFolder.resolve(SEASONS_DIR), e);
        }
    }

    @Override
    public SeasonSettings settings() {
        SeasonSettings defaults = SeasonSettings.defaults();
        try {
            Optional<Path> configFile = existingConfigFile();
            if (configFile.isEmpty()) {
                return defaults;
            }
            ConfigDto config = ConfigFormats.mapperFor(configFile.get())
                    .readValue(Files.readAllBytes(configFile.get()), ConfigDto.class);
            SeasonSettingsDto block = config.season();
            if (block == null) {
                return defaults;
            }
            Duration prepareLead = block.prepareLead() == null
                    ? defaults.prepareLead()
                    : SeasonDtos.parseDuration(block.prepareLead());
            String directory = block.archive() == null || block.archive().directory() == null
                    ? defaults.archiveDirectory()
                    : block.archive().directory();
            boolean compress = block.archive() != null && block.archive().compress() != null
                    ? block.archive().compress()
                    : defaults.archiveCompress();
            Duration minNotice = block.advance() == null || block.advance().minNotice() == null
                    ? defaults.advanceMinNotice()
                    : SeasonDtos.parseDuration(block.advance().minNotice());
            return new SeasonSettings(prepareLead, directory, compress, minNotice);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the season settings in " + dataFolder, e);
        }
    }

    // ----- file layout and DTO mapping -----

    private Season toSeason(SeasonDto dto, Path file) {
        if (dto.id() == null || dto.id().isBlank()) {
            throw new IllegalStateException("season file " + file.getFileName() + " is missing an 'id'");
        }
        try {
            return new Season(
                    new SeasonId(dto.id()),
                    dto.displayName(),
                    Instant.parse(dto.start()),
                    SeasonDtos.parseDuration(dto.duration()),
                    dto.preset(),
                    dto.challenges(),
                    dto.seed());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "season file " + file.getFileName() + " is invalid: " + e.getMessage(), e);
        }
    }

    /** All season files, in a stable order, with a supported config extension. */
    private List<Path> seasonFiles() throws IOException {
        Path seasonsDir = dataFolder.resolve(SEASONS_DIR);
        if (!Files.isDirectory(seasonsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(seasonsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(ConfigFormats::isSupported)
                    .sorted()
                    .toList();
        }
    }

    private Optional<Path> existingConfigFile() {
        for (String ext : CONFIG_EXTENSIONS) {
            Path candidate = dataFolder.resolve(CONFIG_BASE + "." + ext);
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
