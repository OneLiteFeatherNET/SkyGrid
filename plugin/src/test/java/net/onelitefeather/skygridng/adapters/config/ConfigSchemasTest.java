package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for {@link ConfigSchemas}: victools generates a Draft 2020-12 schema from the Jackson
 * DTOs (including the {@code extends} rename and the decoration union), and the schemas are written to
 * the {@code schema/} directory as valid JSON an editor can consume.
 */
class ConfigSchemasTest {

    private final ConfigSchemas schemas = ConfigSchemas.shared();

    @Test
    @DisplayName("the rule schema describes the rule properties")
    void ruleSchemaHasProperties() {
        JsonNode schema = schemas.schema(ConfigSchemas.Kind.RULE);
        assertThat(schema.isObject()).isTrue();
        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode properties = schema.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("block")).isTrue();
        assertThat(properties.has("state")).isTrue();
        assertThat(properties.has("physics")).isTrue();
        assertThat(properties.has("placement")).isTrue();
    }

    @Test
    @DisplayName("the rule schema's placement describes the structure pattern shape")
    void placementSchemaHasStructure() {
        JsonNode placement = schemas.schema(ConfigSchemas.Kind.RULE).get("properties").get("placement");
        assertThat(placement).isNotNull();

        JsonNode placementProperties = placement.get("properties");
        assertThat(placementProperties.has("minHeight")).isTrue();
        assertThat(placementProperties.has("maxHeight")).isTrue();
        assertThat(placementProperties.has("probability")).isTrue();
        assertThat(placementProperties.has("structure")).isTrue();

        JsonNode structureProperties = placementProperties.get("structure").get("properties");
        assertThat(structureProperties.has("filler")).isTrue();
        assertThat(structureProperties.has("legend")).isTrue();
        assertThat(structureProperties.has("layers")).isTrue();
        assertThat(structureProperties.get("layers").get("type").asText()).isEqualTo("array");
    }

    @Test
    @DisplayName("the preset schema describes the preset properties (extends renamed, wfc removed)")
    void presetSchemaHasProperties() {
        JsonNode properties = schemas.schema(ConfigSchemas.Kind.PRESET).get("properties");
        assertThat(properties).isNotNull();
        // 'extends' comes from @JsonProperty("extends") on the parents field.
        assertThat(properties.has("extends")).isTrue();
        assertThat(properties.has("blocks")).isTrue();
        assertThat(properties.has("spawn")).isTrue();
        // The WFC algorithm was removed entirely, so the preset schema no longer has a wfc section.
        assertThat(properties.has("wfc")).isFalse();
    }

    @Test
    @DisplayName("the config schema describes the active-preset selector")
    void configSchemaHasActive() {
        JsonNode properties = schemas.schema(ConfigSchemas.Kind.CONFIG).get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("active")).isTrue();
    }

    @Test
    @DisplayName("writeTo emits the three schema files as valid JSON")
    void writesSchemaFiles(@TempDir Path dir) throws IOException {
        schemas.writeTo(dir);

        ObjectMapper json = new ObjectMapper();
        for (String name : new String[] {"config.schema.json", "preset.schema.json", "rule.schema.json"}) {
            Path file = dir.resolve("schema").resolve(name);
            assertThat(Files.exists(file)).as(name).isTrue();
            JsonNode parsed = json.readTree(Files.readAllBytes(file)); // throws if not valid JSON
            assertThat(parsed.isObject()).isTrue();
        }
    }

    @Test
    @DisplayName("the season schema describes the season file properties")
    void seasonSchemaHasProperties() {
        JsonNode properties = schemas.schema(ConfigSchemas.Kind.SEASON).get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("id")).isTrue();
        assertThat(properties.has("start")).isTrue();
        assertThat(properties.has("duration")).isTrue();
        assertThat(properties.has("preset")).isTrue();
        assertThat(properties.has("challenges")).isTrue();
        assertThat(properties.has("seed")).isTrue();
    }

    @Test
    @DisplayName("writeTo emits the season schema next to the other schemas")
    void writesSeasonSchemaFile(@TempDir Path dir) throws IOException {
        schemas.writeTo(dir);

        Path file = dir.resolve("schema").resolve("season.schema.json");
        assertThat(Files.exists(file)).as("season.schema.json").isTrue();
        assertThat(new ObjectMapper().readTree(Files.readAllBytes(file)).isObject()).isTrue();
    }
}
