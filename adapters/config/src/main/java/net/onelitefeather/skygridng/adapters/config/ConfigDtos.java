package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
            SpawnDto spawn,
            WfcDto wfc
    ) {
    }

    /** A weighted block/loot/mob entry. The id stays a plain namespaced string. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BlockWeightDto(String material, double weight) {
    }

    /** The spread-spawn section. Boxed fields so an omitted key falls back to the domain default. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpawnDto(
            Boolean spread,
            Integer spacing,
            Integer platformY,
            String platformBlock,
            Integer platformRadius,
            Boolean villager
    ) {
    }

    /** The Wave Function Collapse rule set. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record WfcDto(
            SettingsDto settings,
            List<TileDto> tiles,
            List<List<String>> allowed
    ) {
    }

    /** Global WFC generation settings. Boxed so omitted keys keep the domain defaults. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SettingsDto(
            Integer cellSize,
            Integer regionSize,
            Integer surfaceY,
            Integer maxSolverAttempts,
            String fallbackTileId,
            Boolean allowSelf
    ) {
    }

    /** A WFC macro tile type. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TileDto(
            String id,
            Double weight,
            Boolean neutral,
            String surface,
            String fill,
            Boolean liquid,
            List<DecorationDto> features
    ) {
    }

    /**
     * A tile decoration, a discriminated union keyed by the {@code type} property. Jackson injects and
     * consumes {@code type} on both sides, so the concrete records stay clean and round-trip through
     * every format.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PlantDto.class, name = "plant"),
            @JsonSubTypes.Type(value = LootChestDto.class, name = "loot_chest"),
            @JsonSubTypes.Type(value = SpawnerDto.class, name = "spawner")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    sealed interface DecorationDto permits PlantDto, LootChestDto, SpawnerDto {
    }

    /** A plant decoration. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PlantDto(
            String material,
            Boolean requiresLiquidNeighbor,
            String requiresGroundBlock,
            Double probability,
            Integer minHeight,
            Integer maxHeight,
            Boolean providesWater
    ) implements DecorationDto {
    }

    /** A loot-container decoration. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LootChestDto(
            String block,
            String lootTable,
            Double probability
    ) implements DecorationDto {
    }

    /** A monster-spawner decoration. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpawnerDto(
            String block,
            String mob,
            Double probability
    ) implements DecorationDto {
    }
}
