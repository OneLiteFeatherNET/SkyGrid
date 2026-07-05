package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.adapters.config.ConfigDtos.BlockWeightDto;
import net.onelitefeather.skygridng.adapters.config.ConfigDtos.ConfigDto;
import net.onelitefeather.skygridng.adapters.config.ConfigDtos.PresetDto;
import net.onelitefeather.skygridng.adapters.config.ConfigDtos.SpawnDto;
import net.onelitefeather.skygridng.api.model.BiomeId;
import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.Preset;
import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.api.model.SpawnConfig;
import net.onelitefeather.skygridng.ports.out.PresetRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Out adapter: loads and saves the preset catalog with Jackson. The on-disk layout is split in two:
 * a {@code config.<ext>} selector that only names the active preset, and a {@code presets/} directory
 * with one file per preset (the preset name is the file base name). The format is chosen per file from
 * the extension: {@code .json} uses JSON, {@code .yml}/{@code .yaml} YAML, {@code .toml} TOML.
 *
 * A preset file may {@code extends} another (a single id or a list) and override only the parts it
 * changes; inheritance is resolved in the core, not here. This adapter only maps the raw preset files
 * to {@link Preset}/{@link PresetCatalog} and back. A fresh data folder is seeded from the bundled
 * defaults. Block, mob and biome ids stay namespaced strings, so the files are platform-neutral and
 * the api/core never see Jackson.
 */
public final class JacksonPresetRepository implements PresetRepository {

    private static final String CONFIG_BASE = "config";
    private static final String PRESETS_DIR = "presets";
    private static final String DEFAULT_EXTENSION = "yml";

    /** The bundled default file names (relative to the jar config root) seeded on first run. */
    private static final String DEFAULT_CONFIG_RESOURCE = "config.yml";
    private static final List<String> DEFAULT_PRESET_RESOURCES = List.of(
            "presets/classic.yml", "presets/standard.yml", "presets/nether.yml");

    private final Path dataFolder;
    private final BundledDefaults defaults;
    private final ConfigSchemas schemas = ConfigSchemas.shared();

    public JacksonPresetRepository(Path dataFolder, BundledDefaults defaults) {
        this.dataFolder = dataFolder;
        this.defaults = defaults;
    }

    @Override
    public PresetCatalog load() {
        try {
            seedDefaultsIfAbsent();
            schemas.writeTo(dataFolder);

            Path configFile = configFile();
            ConfigDto config = ConfigFormats.mapperFor(configFile).readValue(Files.readAllBytes(configFile), ConfigDto.class);

            Map<String, Preset> presets = new LinkedHashMap<>();
            for (Map.Entry<String, Path> entry : presetFiles().entrySet()) {
                String name = entry.getKey();
                Path file = entry.getValue();
                PresetDto dto = ConfigFormats.mapperFor(file).readValue(Files.readAllBytes(file), PresetDto.class);
                presets.put(name, toPreset(name, dto));
            }
            if (presets.isEmpty()) {
                throw new IllegalStateException("no presets found in " + dataFolder.resolve(PRESETS_DIR));
            }
            String active = config.active() != null ? config.active() : presets.keySet().iterator().next();
            return new PresetCatalog(presets, active);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load configuration in " + dataFolder, e);
        }
    }

    @Override
    public void save(PresetCatalog catalog) {
        try {
            Files.createDirectories(dataFolder);
            Path presetsDir = dataFolder.resolve(PRESETS_DIR);
            Files.createDirectories(presetsDir);

            Path configFile = existingConfigFile().orElse(dataFolder.resolve(CONFIG_BASE + "." + DEFAULT_EXTENSION));
            writeValue(configFile, new ConfigDto(catalog.active()));

            for (Preset preset : catalog.presets().values()) {
                Path file = presetFileFor(presetsDir, preset.name());
                writeValue(file, toDto(preset));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save configuration in " + dataFolder, e);
        }
    }

    // ----- seeding and file layout -----

    private void seedDefaultsIfAbsent() throws IOException {
        if (existingConfigFile().isPresent()) {
            return;
        }
        Files.createDirectories(dataFolder.resolve(PRESETS_DIR));
        seedResource(DEFAULT_CONFIG_RESOURCE);
        for (String resource : DEFAULT_PRESET_RESOURCES) {
            seedResource(resource);
        }
    }

    private void seedResource(String resource) throws IOException {
        try (InputStream in = defaults.open(resource)) {
            if (in == null) {
                throw new IllegalStateException("bundled default " + resource + " not found");
            }
            Path target = dataFolder.resolve(resource);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, in.readAllBytes());
        }
    }

    private Path configFile() throws IOException {
        return existingConfigFile()
                .orElseThrow(() -> new IllegalStateException("no config file in " + dataFolder));
    }

    private Optional<Path> existingConfigFile() {
        for (String ext : List.of("json", "yml", "yaml", "toml")) {
            Path candidate = dataFolder.resolve(CONFIG_BASE + "." + ext);
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** All preset files by name (base name), in a stable alphabetical order. */
    private Map<String, Path> presetFiles() throws IOException {
        Path presetsDir = dataFolder.resolve(PRESETS_DIR);
        Map<String, Path> byName = new TreeMap<>();
        if (!Files.isDirectory(presetsDir)) {
            return byName;
        }
        try (Stream<Path> files = Files.list(presetsDir)) {
            files.filter(Files::isRegularFile)
                    .filter(ConfigFormats::isSupported)
                    .forEach(file -> byName.put(ConfigFormats.baseName(file), file));
        }
        return byName;
    }

    /** The target file for a preset on save: keep an existing file's format, else default to YAML. */
    private Path presetFileFor(Path presetsDir, String name) throws IOException {
        if (Files.isDirectory(presetsDir)) {
            try (Stream<Path> files = Files.list(presetsDir)) {
                Optional<Path> existing = files
                        .filter(Files::isRegularFile)
                        .filter(ConfigFormats::isSupported)
                        .filter(file -> ConfigFormats.baseName(file).equals(name))
                        .findFirst();
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
        }
        return presetsDir.resolve(name + "." + ConfigFormats.DEFAULT_EXTENSION);
    }

    private void writeValue(Path file, Object value) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        byte[] bytes = ConfigFormats.mapperFor(file).writeValueAsBytes(value);
        Files.write(file, bytes);
    }

    // ----- DTO -> domain (null override field stays null = "inherit") -----

    private Preset toPreset(String name, PresetDto dto) {
        return new Preset(
                name,
                dto.parents() == null ? List.of() : List.copyOf(dto.parents()),
                dto.algorithm(),
                dto.spacing(),
                dto.minY(),
                dto.maxY(),
                dto.noiseScale(),
                toWeights(dto.blocks()),
                toWeights(dto.chestLoot()),
                toWeights(dto.spawnerMobs()),
                toBiomes(dto.biomes()),
                toSpawn(dto.spawn()));
    }

    private List<BlockWeight> toWeights(List<BlockWeightDto> dtos) {
        if (dtos == null) {
            return null;
        }
        List<BlockWeight> weights = new ArrayList<>();
        for (BlockWeightDto dto : dtos) {
            weights.add(new BlockWeight(BlockId.of(dto.material()), dto.weight()));
        }
        return weights;
    }

    private List<BiomeId> toBiomes(List<String> ids) {
        if (ids == null) {
            return null;
        }
        List<BiomeId> biomes = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                biomes.add(BiomeId.of(id));
            }
        }
        return biomes;
    }

    private SpawnConfig toSpawn(SpawnDto dto) {
        if (dto == null) {
            return null;
        }
        SpawnConfig d = SpawnConfig.defaults();
        return new SpawnConfig(
                dto.spread() != null ? dto.spread() : d.spread(),
                dto.spacing() != null ? dto.spacing() : d.spacing(),
                dto.searchRadius() != null ? dto.searchRadius() : d.searchRadius(),
                dto.maxAttempts() != null ? dto.maxAttempts() : d.maxAttempts());
    }

    // ----- domain -> DTO (only non-null override fields are written) -----

    private PresetDto toDto(Preset preset) {
        return new PresetDto(
                preset.parents().isEmpty() ? null : List.copyOf(preset.parents()),
                preset.algorithm(),
                preset.spacing(),
                preset.minY(),
                preset.maxY(),
                preset.noiseScale(),
                weightsToDto(preset.blocks()),
                weightsToDto(preset.chestLoot()),
                weightsToDto(preset.spawnerMobs()),
                biomesToDto(preset.biomes()),
                spawnToDto(preset.spawn()));
    }

    private List<BlockWeightDto> weightsToDto(List<BlockWeight> weights) {
        if (weights == null) {
            return null;
        }
        List<BlockWeightDto> dtos = new ArrayList<>();
        for (BlockWeight weight : weights) {
            dtos.add(new BlockWeightDto(weight.material().asString(), weight.weight()));
        }
        return dtos;
    }

    private List<String> biomesToDto(List<BiomeId> biomes) {
        if (biomes == null) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (BiomeId biome : biomes) {
            ids.add(biome.asString());
        }
        return ids;
    }

    private SpawnDto spawnToDto(SpawnConfig spawn) {
        if (spawn == null) {
            return null;
        }
        return new SpawnDto(
                spawn.spread(),
                spawn.spacing(),
                spawn.searchRadius(),
                spawn.maxAttempts());
    }
}
