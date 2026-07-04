package net.onelitefeather.skygridng.api.model;

import java.util.List;

/**
 * Top level, immutable configuration aggregate.
 *
 * Holds the selected algorithm id, the configuration for each algorithm, the spread-spawn settings
 * and the biome palette. Acts as a Memento for in game edits and undo. The {@code with...} methods
 * return new instances. A {@link Preset} resolves to one of these.
 *
 * @param algorithm one of "wfc", "skygrid", "noise_skygrid"
 * @param wfc       Wave Function Collapse configuration (tiles, adjacency, settings)
 * @param skygrid   SkyGrid configuration (spacing, block table)
 * @param spawn     spread-spawn configuration (first-join placement)
 * @param biomes    the biome palette assigned across the world
 */
public record GeneratorConfig(
        String algorithm,
        RuleSet wfc,
        SkyGridConfig skygrid,
        SpawnConfig spawn,
        List<BiomeId> biomes
) {
    /** The default overworld biome palette, used when a config omits the biome list. */
    public static final List<BiomeId> DEFAULT_BIOMES = List.of(
            BiomeId.of("minecraft:plains"),
            BiomeId.of("minecraft:forest"),
            BiomeId.of("minecraft:birch_forest"),
            BiomeId.of("minecraft:dark_forest"),
            BiomeId.of("minecraft:taiga"),
            BiomeId.of("minecraft:snowy_plains"),
            BiomeId.of("minecraft:desert"),
            BiomeId.of("minecraft:savanna"),
            BiomeId.of("minecraft:jungle"),
            BiomeId.of("minecraft:swamp"));

    public GeneratorConfig {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        if (wfc == null) {
            throw new IllegalArgumentException("wfc config is required");
        }
        if (skygrid == null) {
            throw new IllegalArgumentException("skygrid config is required");
        }
        if (spawn == null) {
            throw new IllegalArgumentException("spawn config is required");
        }
        if (biomes == null || biomes.isEmpty()) {
            throw new IllegalArgumentException("at least one biome is required");
        }
        biomes = List.copyOf(biomes);
    }

    /** Convenience constructor that applies the default spawn and biome settings (older call sites). */
    public GeneratorConfig(String algorithm, RuleSet wfc, SkyGridConfig skygrid) {
        this(algorithm, wfc, skygrid, SpawnConfig.defaults(), DEFAULT_BIOMES);
    }

    /** Convenience constructor that applies the default biome palette. */
    public GeneratorConfig(String algorithm, RuleSet wfc, SkyGridConfig skygrid, SpawnConfig spawn) {
        this(algorithm, wfc, skygrid, spawn, DEFAULT_BIOMES);
    }

    public GeneratorConfig withAlgorithm(String newAlgorithm) {
        return new GeneratorConfig(newAlgorithm, wfc, skygrid, spawn, biomes);
    }

    public GeneratorConfig withWfc(RuleSet newWfc) {
        return new GeneratorConfig(algorithm, newWfc, skygrid, spawn, biomes);
    }

    public GeneratorConfig withSkygrid(SkyGridConfig newSkygrid) {
        return new GeneratorConfig(algorithm, wfc, newSkygrid, spawn, biomes);
    }

    public GeneratorConfig withSpawn(SpawnConfig newSpawn) {
        return new GeneratorConfig(algorithm, wfc, skygrid, newSpawn, biomes);
    }

    public GeneratorConfig withBiomes(List<BiomeId> newBiomes) {
        return new GeneratorConfig(algorithm, wfc, skygrid, spawn, newBiomes);
    }
}
