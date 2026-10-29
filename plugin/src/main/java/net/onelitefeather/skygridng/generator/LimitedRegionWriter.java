package net.onelitefeather.skygridng.generator;

import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.loot.LootTable;

import java.util.Map;

/**
 * {@link RegionWriter} implementation wrapping a live Paper {@link LimitedRegion}: the seam that lets
 * the framework-free decoration and structure logic in {@code generator} run against a real
 * populator region without depending on Bukkit itself. Constructed fresh for each
 * {@code BlockPopulator.populate} call, alongside the {@link RuleResolver} that already resolves a
 * {@link BlockId} to native block data for a live generation call; never held as a long lived or
 * injected singleton.
 */
final class LimitedRegionWriter implements RegionWriter {

    private final LimitedRegion area;
    private final RuleResolver rules;

    LimitedRegionWriter(LimitedRegion area, RuleResolver rules) {
        this.area = area;
        this.rules = rules;
    }

    @Override
    public boolean isInRegion(int x, int y, int z) {
        return area.isInRegion(x, y, z);
    }

    @Override
    public BlockId blockAt(int x, int y, int z) {
        Material material = area.getType(x, y, z);
        return BlockId.of(material.getKey().asString());
    }

    @Override
    public void place(int x, int y, int z, BlockId block) {
        area.setBlockData(x, y, z, rules.blockData(block));
    }

    @Override
    public void place(int x, int y, int z, BlockId block, Map<String, String> state) {
        area.setBlockData(x, y, z, rules.blockData(block, state));
    }

    @Override
    public void scheduleUpdate(int x, int y, int z, PhysicsMode mode) {
        switch (mode) {
            case NONE -> { }
            case BLOCK_UPDATE -> area.scheduleBlockUpdate(x, y, z);
            case FLUID_UPDATE -> area.scheduleFluidUpdate(x, y, z);
        }
    }

    @Override
    public void fillLootChest(int x, int y, int z, BlockId lootTable, long seed) {
        LootTable table = BukkitIds.lootTable(lootTable);
        if (table != null && area.getBlockState(x, y, z) instanceof Chest chest) {
            chest.setLootTable(table);
            chest.setSeed(seed);
            chest.update(true, false);
        }
    }

    @Override
    public void fillSpawner(int x, int y, int z, BlockId mobType) {
        EntityType mob = BukkitIds.entityType(mobType);
        if (mob != null && area.getBlockState(x, y, z) instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(mob);
            spawner.update(true, false);
        }
    }
}
