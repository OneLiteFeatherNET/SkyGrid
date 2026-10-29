package net.onelitefeather.skygridng.generator;

import java.util.List;

/**
 * Configuration shared by both SkyGrid algorithms (independent and noise clustered).
 *
 * @param spacing    grid length in blocks (classic SkyGrid is 4)
 * @param minY       lowest grid layer (inclusive)
 * @param maxY       highest grid layer (inclusive)
 * @param noiseScale  frequency for the noise clustered variant (smaller means larger clusters)
 * @param blocks      weighted block table
 * @param chestLoot   weighted loot-table ids for table-placed chests (the {@link BlockWeight} id is a
 *                    loot-table id like {@code minecraft:chests/simple_dungeon}); empty means bare chests
 * @param spawnerMobs weighted mob ids for table-placed spawners (the id is an entity id like
 *                    {@code minecraft:zombie}); empty means bare spawners
 */
public record SkyGridConfig(
        int spacing,
        int minY,
        int maxY,
        double noiseScale,
        List<BlockWeight> blocks,
        List<BlockWeight> chestLoot,
        List<BlockWeight> spawnerMobs
) {
    public SkyGridConfig {
        if (spacing < 1) {
            throw new IllegalArgumentException("spacing must be >= 1");
        }
        if (maxY < minY) {
            throw new IllegalArgumentException("maxY must be >= minY");
        }
        if (noiseScale <= 0.0) {
            throw new IllegalArgumentException("noiseScale must be > 0");
        }
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("SkyGrid needs at least one block");
        }
        blocks = List.copyOf(blocks);
        chestLoot = chestLoot == null ? List.of() : List.copyOf(chestLoot);
        spawnerMobs = spawnerMobs == null ? List.of() : List.copyOf(spawnerMobs);
    }

    /** Replaces or adds a block weight, returning a new config (Memento friendly). */
    public SkyGridConfig withBlockWeight(BlockId material, double weight) {
        boolean found = blocks.stream().anyMatch(b -> b.material().equals(material));
        List<BlockWeight> updated = found
                ? blocks.stream()
                        .map(b -> b.material().equals(material) ? new BlockWeight(material, weight) : b)
                        .toList()
                : concat(blocks, new BlockWeight(material, weight));
        return new SkyGridConfig(spacing, minY, maxY, noiseScale, updated, chestLoot, spawnerMobs);
    }

    private static List<BlockWeight> concat(List<BlockWeight> base, BlockWeight extra) {
        java.util.ArrayList<BlockWeight> copy = new java.util.ArrayList<>(base);
        copy.add(extra);
        return copy;
    }
}
