package net.onelitefeather.skygridng.api.model;

import java.util.List;

/**
 * A named generation profile that may inherit from another preset and override only the parts it
 * changes.
 *
 * A {@code null} field means "inherit from the parents (or, at the root, fall back to the built-in
 * default)". The resolver flattens the {@code extends} graph into a complete {@link GeneratorConfig},
 * so a {@code standard} or {@code nether} preset can restate just its block palette or mob list on top
 * of {@code classic}. This mirrors the inheritance model used by CMake presets and Minecraft world
 * presets (override only what differs).
 *
 * A preset may extend several parents. They are applied left to right, so a later parent overrides
 * an earlier one, and this preset's own fields override all of them.
 *
 * @param name        the preset id (the key it is stored under)
 * @param parents     the presets this one extends, applied left to right (empty for a root preset)
 * @param algorithm   override: the active algorithm id
 * @param spacing     override: SkyGrid grid spacing
 * @param minY        override: lowest grid layer
 * @param maxY        override: highest grid layer
 * @param noiseScale  override: noise scale for {@code noise_skygrid}
 * @param blocks      override: the weighted block palette
 * @param chestLoot   override: loot tables rolled for chests
 * @param spawnerMobs override: mobs assigned to spawners
 * @param biomes      override: the biome palette
 * @param spawn       override: spread-spawn settings
 */
public record Preset(
        String name,
        List<String> parents,
        String algorithm,
        Integer spacing,
        Integer minY,
        Integer maxY,
        Double noiseScale,
        List<BlockWeight> blocks,
        List<BlockWeight> chestLoot,
        List<BlockWeight> spawnerMobs,
        List<BiomeId> biomes,
        SpawnConfig spawn
) {
    public Preset {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset.name must not be blank");
        }
        parents = parents == null ? List.of() : List.copyOf(parents);
        // null stays null (means "inherit"); only defensively copy the lists that are present.
        blocks = blocks == null ? null : List.copyOf(blocks);
        chestLoot = chestLoot == null ? null : List.copyOf(chestLoot);
        spawnerMobs = spawnerMobs == null ? null : List.copyOf(spawnerMobs);
        biomes = biomes == null ? null : List.copyOf(biomes);
    }
}
