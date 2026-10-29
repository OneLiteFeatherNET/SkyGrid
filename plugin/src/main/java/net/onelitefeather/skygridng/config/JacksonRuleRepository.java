package net.onelitefeather.skygridng.config;

import net.onelitefeather.skygridng.config.RuleDtos.LegendEntryDto;
import net.onelitefeather.skygridng.config.RuleDtos.PlacementDto;
import net.onelitefeather.skygridng.config.RuleDtos.RuleDto;
import net.onelitefeather.skygridng.config.RuleDtos.StructureDto;
import net.onelitefeather.skygridng.generator.BlockId;
import net.onelitefeather.skygridng.generator.LegendEntry;
import net.onelitefeather.skygridng.generator.PhysicsMode;
import net.onelitefeather.skygridng.generator.PlacementRule;
import net.onelitefeather.skygridng.generator.Rule;
import net.onelitefeather.skygridng.generator.RuleCatalog;
import net.onelitefeather.skygridng.generator.StructurePattern;
import net.onelitefeather.skygridng.generator.RuleRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Out adapter: loads and saves the per-block rule catalog with Jackson, one file per block under the
 * {@code rules/} directory ({@code rules/oak_leaves.yml}, {@code rules/sugar_cane.toml}, ...). The block
 * id inside the file is authoritative; the file name is only there for humans. The format is chosen per
 * file from its extension (JSON, YAML or TOML). A fresh {@code rules/} directory is seeded from the
 * bundled defaults. Ids stay namespaced strings, so the files are platform-neutral and the api/core
 * never see Jackson.
 */
public final class JacksonRuleRepository implements RuleRepository {

    private static final String RULES_DIR = "rules";
    private static final List<String> DEFAULT_RULE_RESOURCES = List.of(
            "rules/oak_leaves.yml", "rules/cherry_leaves.yml", "rules/golden_dandelion.yml",
            "rules/sugar_cane.yml", "rules/water.yml");

    private final Path dataFolder;
    private final BundledDefaults defaults;
    private final ConfigSchemas schemas = ConfigSchemas.shared();

    public JacksonRuleRepository(Path dataFolder, BundledDefaults defaults) {
        this.dataFolder = dataFolder;
        this.defaults = defaults;
    }

    @Override
    public RuleCatalog load() {
        try {
            seedDefaultsIfAbsent();
            schemas.writeTo(dataFolder);

            Map<BlockId, Rule> rules = new LinkedHashMap<>();
            for (Path file : ruleFiles()) {
                RuleDto dto = ConfigFormats.mapperFor(file).readValue(Files.readAllBytes(file), RuleDto.class);
                Rule rule = toRule(dto, file);
                rules.put(rule.block(), rule);
            }
            return new RuleCatalog(rules);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load rules in " + dataFolder.resolve(RULES_DIR), e);
        }
    }

    @Override
    public void save(RuleCatalog catalog) {
        try {
            Path rulesDir = dataFolder.resolve(RULES_DIR);
            Files.createDirectories(rulesDir);
            for (Rule rule : catalog.rules().values()) {
                Path file = ruleFileFor(rulesDir, rule.block());
                byte[] bytes = ConfigFormats.mapperFor(file).writeValueAsBytes(toDto(rule));
                Files.write(file, bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save rules in " + dataFolder.resolve(RULES_DIR), e);
        }
    }

    // ----- seeding and file layout -----

    private void seedDefaultsIfAbsent() throws IOException {
        Path rulesDir = dataFolder.resolve(RULES_DIR);
        if (Files.isDirectory(rulesDir) && !ruleFiles().isEmpty()) {
            return;
        }
        Files.createDirectories(rulesDir);
        for (String resource : DEFAULT_RULE_RESOURCES) {
            try (InputStream in = defaults.open(resource)) {
                if (in == null) {
                    throw new IllegalStateException("bundled default " + resource + " not found");
                }
                Files.write(dataFolder.resolve(resource), in.readAllBytes());
            }
        }
    }

    private List<Path> ruleFiles() throws IOException {
        Path rulesDir = dataFolder.resolve(RULES_DIR);
        if (!Files.isDirectory(rulesDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(rulesDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(ConfigFormats::isSupported)
                    .sorted()
                    .toList();
        }
    }

    /** The target file for a rule on save: keep an existing file's format, else default to YAML. */
    private Path ruleFileFor(Path rulesDir, BlockId block) throws IOException {
        String base = fileBaseFor(block);
        if (Files.isDirectory(rulesDir)) {
            try (Stream<Path> files = Files.list(rulesDir)) {
                Optional<Path> existing = files
                        .filter(Files::isRegularFile)
                        .filter(ConfigFormats::isSupported)
                        .filter(file -> ConfigFormats.baseName(file).equals(base))
                        .findFirst();
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
        }
        return rulesDir.resolve(base + "." + ConfigFormats.DEFAULT_EXTENSION);
    }

    /** A human-friendly, collision-free file base for a block id. */
    private static String fileBaseFor(BlockId block) {
        return block.namespace().equals(BlockId.DEFAULT_NAMESPACE)
                ? block.value()
                : block.namespace() + "_" + block.value();
    }

    // ----- DTO <-> domain -----

    private Rule toRule(RuleDto dto, Path file) {
        if (dto.block() == null || dto.block().isBlank()) {
            throw new IllegalStateException("rule file " + file.getFileName() + " is missing a 'block'");
        }
        Map<String, String> state = dto.state() == null ? Map.of() : dto.state();
        return new Rule(
                BlockId.of(dto.block()), state, physics(dto.physics(), file), placement(dto.placement(), file));
    }

    private PlacementRule placement(PlacementDto dto, Path file) {
        if (dto == null) {
            return null;
        }
        if (dto.structure() == null) {
            throw new IllegalStateException(
                    "rule file " + file.getFileName() + " has a 'placement' block but is missing 'structure'");
        }
        return new PlacementRule(
                structure(dto.structure(), file),
                dto.minHeight() != null ? dto.minHeight() : 1,
                dto.maxHeight() != null ? dto.maxHeight() : 1,
                dto.probability() != null ? dto.probability() : 0.0);
    }

    private StructurePattern structure(StructureDto dto, Path file) {
        Map<Character, LegendEntry> legend = new LinkedHashMap<>();
        if (dto.legend() != null) {
            for (Map.Entry<String, LegendEntryDto> entry : dto.legend().entrySet()) {
                String key = entry.getKey();
                if (key == null || key.length() != 1) {
                    throw new IllegalStateException("rule file " + file.getFileName()
                            + " has an invalid structure legend key '" + key + "' (must be exactly one character)");
                }
                char symbol = key.charAt(0);
                if (symbol == StructurePattern.IGNORE || symbol == StructurePattern.ENSURE_SOLID
                        || symbol == StructurePattern.CLEAR_AIR) {
                    throw new IllegalStateException("rule file " + file.getFileName()
                            + " has a structure legend entry '" + symbol + "' that reuses a reserved symbol");
                }
                LegendEntryDto value = entry.getValue();
                legend.put(symbol, new LegendEntry(
                        BlockId.of(value.block()),
                        value.state() == null ? Map.of() : value.state(),
                        value.anchor() != null && value.anchor()));
            }
        }
        List<List<String>> layers = dto.layers() == null ? List.of() : dto.layers();
        BlockId filler = dto.filler() == null || dto.filler().isBlank() ? null : BlockId.of(dto.filler());
        try {
            return new StructurePattern(layers, legend, filler);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "invalid structure pattern in " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private RuleDto toDto(Rule rule) {
        return new RuleDto(
                rule.block().asString(),
                rule.state().isEmpty() ? null : new LinkedHashMap<>(rule.state()),
                rule.physics().name().toLowerCase(Locale.ROOT),
                placementToDto(rule.placement()));
    }

    private PlacementDto placementToDto(PlacementRule placement) {
        if (placement == null) {
            return null;
        }
        return new PlacementDto(
                structureToDto(placement.structure()),
                placement.minHeight(),
                placement.maxHeight(),
                placement.probability());
    }

    private StructureDto structureToDto(StructurePattern structure) {
        Map<String, LegendEntryDto> legend = new LinkedHashMap<>();
        for (Map.Entry<Character, LegendEntry> entry : structure.legend().entrySet()) {
            LegendEntry value = entry.getValue();
            legend.put(String.valueOf(entry.getKey()), new LegendEntryDto(
                    value.block().asString(),
                    value.state().isEmpty() ? null : new LinkedHashMap<>(value.state()),
                    value.anchor() ? Boolean.TRUE : null));
        }
        return new StructureDto(
                structure.filler() == null ? null : structure.filler().asString(),
                legend,
                structure.layers());
    }

    private static PhysicsMode physics(String raw, Path file) {
        if (raw == null || raw.isBlank()) {
            return PhysicsMode.NONE;
        }
        try {
            return PhysicsMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("unknown physics '" + raw + "' in " + file.getFileName()
                    + " (use none, block_update or fluid_update)");
        }
    }
}
