package net.onelitefeather.skygridng.adapters.config;

import net.onelitefeather.skygridng.adapters.config.RuleDtos.PlacementDto;
import net.onelitefeather.skygridng.adapters.config.RuleDtos.RuleDto;
import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.PhysicsMode;
import net.onelitefeather.skygridng.api.model.PlacementRule;
import net.onelitefeather.skygridng.api.model.Rule;
import net.onelitefeather.skygridng.api.model.RuleCatalog;
import net.onelitefeather.skygridng.api.model.WaterMode;
import net.onelitefeather.skygridng.ports.out.RuleRepository;

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
            "rules/oak_leaves.yml", "rules/cherry_leaves.yml", "rules/sugar_cane.yml", "rules/water.yml");

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
        return new Rule(BlockId.of(dto.block()), state, physics(dto.physics(), file), placement(dto.placement()));
    }

    private PlacementRule placement(PlacementDto dto) {
        if (dto == null) {
            return null;
        }
        return new PlacementRule(
                dto.requiresGround() == null || dto.requiresGround().isBlank() ? null : BlockId.of(dto.requiresGround()),
                dto.requiresLiquidNeighbor() != null && dto.requiresLiquidNeighbor(),
                water(dto.water()),
                dto.minHeight() != null ? dto.minHeight() : 1,
                dto.maxHeight() != null ? dto.maxHeight() : 1,
                dto.probability() != null ? dto.probability() : 0.0);
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
                placement.requiresGround() == null ? null : placement.requiresGround().asString(),
                placement.requiresLiquidNeighbor() ? Boolean.TRUE : null,
                placement.water().name().toLowerCase(Locale.ROOT),
                placement.minHeight(),
                placement.maxHeight(),
                placement.probability());
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

    private static WaterMode water(String raw) {
        if (raw == null || raw.isBlank()) {
            return WaterMode.NONE;
        }
        try {
            return WaterMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("unknown water mode '" + raw + "' (use none, source or waterlogged)");
        }
    }
}
