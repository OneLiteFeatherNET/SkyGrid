package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Adapter-internal Jackson data-transfer objects that mirror the on-disk preset schema one to one.
 *
 * They exist so the api and core never see a Jackson annotation: block, biome, mob and loot ids stay
 * plain {@link String}s here (mapped to {@code BlockId}/{@code BiomeId} in {@link JacksonPresetRepository})
 * and every override field is boxed so a missing key round-trips as {@code null} ("inherit") instead of
 * a primitive default. The same records serialize to JSON, YAML and TOML unchanged. Keeping the key
 * names literal here also lets a later JSON Schema generator describe the config from these types.
 */
final class ConfigDtos {

    private ConfigDtos() {
    }

    /** The {@code config.<ext>} selector file: only the active preset id. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ConfigDto(String active) {
    }

    /**
     * One preset body (stored as {@code presets/<name>.<ext>}). The preset name is the file base name,
     * so it is deliberately not a field here. A {@code null} field means "inherit".
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PresetDto(
            @JsonProperty("extends") List<String> parents,
            String algorithm,
            Integer spacing,
            Integer minY,
            Integer maxY,
            Double noiseScale,
            List<BlockWeightDto> blocks,
            List<BlockWeightDto> chestLoot,
            List<BlockWeightDto> spawnerMobs,
            List<String> biomes,
            SpawnDto spawn
    ) {
    }

    /** A weighted block/loot/mob entry. The id stays a plain namespaced string. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BlockWeightDto(String material, double weight) {
    }

    /**
     * The spread-spawn section. Boxed fields so an omitted key falls back to the domain default.
     * {@code searchRadius} and {@code maxAttempts} bound the safe-block search: the adapter probes
     * outward from the assigned slot for a standable block and teleports the player there instead of
     * building a platform.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpawnDto(
            Boolean spread,
            Integer spacing,
            Integer searchRadius,
            Integer maxAttempts
    ) {
    }
}
