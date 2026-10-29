package net.onelitefeather.skygridng.config;

import net.onelitefeather.skygridng.config.ConfigDtos.ConfigDto;
import net.onelitefeather.skygridng.config.ConfigDtos.SeasonSettingsDto;
import net.onelitefeather.skygridng.config.SeasonDtos.SeasonDto;
import net.onelitefeather.skygridng.season.Season;
import net.onelitefeather.skygridng.season.SeasonCatalog;
import net.onelitefeather.skygridng.season.SeasonId;
import net.onelitefeather.skygridng.season.SeasonSettings;
import net.onelitefeather.skygridng.season.SeasonRepository;

import java.io.IOException;
import java.io.InputStream;
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
 * file from its extension ({@code .yml} or {@code .yaml}). The id inside the file is authoritative. Files with any other
 * extension, such as {@code example.yml.disabled}, are not loaded. The adapter only maps the files to
 * {@link Season}; the core validates presets, challenges and overlaps. An absent or empty {@code seasons/}
 * directory yields no seasons, which the core reads as the implicit legacy season.
 */
public final class JacksonSeasonRepository implements SeasonRepository {

    private static final String SEASONS_DIR = "seasons";
    private static final String CONFIG_BASE = "config";
    private static final List<String> CONFIG_EXTENSIONS = List.of("yml", "yaml");

    private static final String EXAMPLE = "example.yml.disabled";

    private final Path dataFolder;
    private final BundledDefaults defaults;

    public JacksonSeasonRepository(Path dataFolder) {
        this(dataFolder, path -> null);
    }

    /**
     * @param defaults supplies the bundled {@code seasons/example.yml.disabled}, which is written when the
     *                 {@code seasons/} directory is absent; an absent bundled example is skipped
     */
    public JacksonSeasonRepository(Path dataFolder, BundledDefaults defaults) {
        this.dataFolder = dataFolder;
        this.defaults = defaults;
    }

    @Override
    public SeasonCatalog load() {
        try {
            ConfigFormats.warnAboutUnsupportedFiles(dataFolder.resolve(SEASONS_DIR));
            seedExampleIfAbsent();

            List<Season> seasons = new ArrayList<>();
            for (Path file : seasonFiles()) {
                SeasonDto dto = ConfigFormats.mapper().readValue(Files.readAllBytes(file), SeasonDto.class);
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
            ConfigDto config = ConfigFormats.mapper()
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
            Duration minNotice = block.advance() == null || block.advance().minNotice() == null
                    ? defaults.advanceMinNotice()
                    : SeasonDtos.parseDuration(block.advance().minNotice());
            return new SeasonSettings(prepareLead, directory, minNotice);
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

    /** An absent {@code seasons/} directory gets the disabled example; an existing one (even empty) is left alone. */
    private void seedExampleIfAbsent() throws IOException {
        Path seasonsDir = dataFolder.resolve(SEASONS_DIR);
        if (Files.isDirectory(seasonsDir)) {
            return;
        }
        try (InputStream bundled = defaults.open(SEASONS_DIR + "/" + EXAMPLE)) {
            if (bundled == null) {
                return;
            }
            Files.createDirectories(seasonsDir);
            Files.copy(bundled, seasonsDir.resolve(EXAMPLE));
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
