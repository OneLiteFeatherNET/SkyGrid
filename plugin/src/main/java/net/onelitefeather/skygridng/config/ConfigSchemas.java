package net.onelitefeather.skygridng.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the JSON Schema for the on-disk config. victools reads the Jackson annotations on the DTOs
 * ({@link ConfigDtos}, {@link RuleDtos} and {@link ChallengeDtos}) to emit a Draft 2020-12 schema for
 * each root file:
 *
 * <ul>
 *   <li>{@code config.<ext>} maps to {@link ConfigDtos.ConfigDto} (the active-preset selector),</li>
 *   <li>a {@code presets/<name>.<ext>} file maps to {@link ConfigDtos.PresetDto},</li>
 *   <li>a {@code rules/<block>.<ext>} file maps to {@link RuleDtos.RuleDto},</li>
 *   <li>a {@code challenges/<id>.<ext>} file maps to {@link ChallengeDtos.ChallengeDto},</li>
 *   <li>a {@code seasons/<id>.<ext>} file maps to {@link SeasonDtos.SeasonDto}.</li>
 * </ul>
 *
 * The schemas describe the structure only, so they are format-neutral: an editor (a YAML or JSON
 * language server) can use them to validate and autocomplete the YAML, TOML or JSON an admin writes,
 * without the server having to ship a runtime validator. Generation is a one-off, so a single shared
 * instance is reused across the repositories.
 */
final class ConfigSchemas {

    /** The five schema kinds and their on-disk schema file names (under {@code schema/}). */
    enum Kind {
        CONFIG("schema/config.schema.json"),
        PRESET("schema/preset.schema.json"),
        RULE("schema/rule.schema.json"),
        CHALLENGE("schema/challenge.schema.json"),
        SEASON("schema/season.schema.json");

        private final String fileName;

        Kind(String fileName) {
            this.fileName = fileName;
        }
    }

    private static final ConfigSchemas SHARED = new ConfigSchemas();

    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectNode configSchema;
    private final ObjectNode presetSchema;
    private final ObjectNode ruleSchema;
    private final ObjectNode challengeSchema;
    private final ObjectNode seasonSchema;

    private ConfigSchemas() {
        SchemaGenerator generator = newGenerator();
        this.configSchema = generator.generateSchema(ConfigDtos.ConfigDto.class);
        this.presetSchema = generator.generateSchema(ConfigDtos.PresetDto.class);
        this.ruleSchema = generator.generateSchema(RuleDtos.RuleDto.class);
        this.challengeSchema = generator.generateSchema(ChallengeDtos.ChallengeDto.class);
        this.seasonSchema = generator.generateSchema(SeasonDtos.SeasonDto.class);
    }

    /** The shared instance; the schemas are generated once and reused. */
    static ConfigSchemas shared() {
        return SHARED;
    }

    private static SchemaGenerator newGenerator() {
        // PLAIN_JSON is the neutral Draft 2020-12 option preset; the Jackson module teaches victools to
        // read @JsonProperty and @JsonInclude on the DTOs.
        JacksonModule jacksonModule = new JacksonModule();
        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(jacksonModule);
        SchemaGeneratorConfig config = builder.build();
        return new SchemaGenerator(config);
    }

    /** The generated schema for a kind, as a Jackson node. */
    JsonNode schema(Kind kind) {
        return switch (kind) {
            case CONFIG -> configSchema;
            case PRESET -> presetSchema;
            case RULE -> ruleSchema;
            case CHALLENGE -> challengeSchema;
            case SEASON -> seasonSchema;
        };
    }

    /**
     * Writes the generated schemas as pretty-printed JSON into {@code <dataFolder>/schema/}.
     * Idempotent: the files are overwritten so an admin always sees the schema for the running build.
     */
    void writeTo(Path dataFolder) {
        try {
            for (Kind kind : Kind.values()) {
                Path target = dataFolder.resolve(kind.fileName);
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema(kind));
                Files.write(target, json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write JSON schema files in " + dataFolder, e);
        }
    }
}
