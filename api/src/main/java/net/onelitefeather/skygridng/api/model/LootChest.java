package net.onelitefeather.skygridng.api.model;

/**
 * A loot container decoration: a chest-like block filled from a Minecraft loot table.
 *
 * The loot table is a namespaced id (for example {@code minecraft:chests/simple_dungeon}); the
 * adapter resolves it and fills the placed container lazily, so admins can point a chest at any
 * vanilla or datapack loot table.
 *
 * @param block       the container block (for example {@code minecraft:chest})
 * @param lootTable   the loot table id
 * @param probability spawn probability per column in [0,1]
 */
public record LootChest(
        BlockId block,
        BlockId lootTable,
        double probability
) implements Decoration {
    public LootChest {
        if (block == null || lootTable == null) {
            throw new IllegalArgumentException("LootChest.block and lootTable must not be null");
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]: " + lootTable);
        }
    }
}
