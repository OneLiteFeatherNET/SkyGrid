package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.PlacementRule;
import net.onelitefeather.skygridng.api.model.Rule;
import net.onelitefeather.skygridng.api.util.Seeds;
import net.onelitefeather.skygridng.ports.in.SkyGridDecorations;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.loot.LootTable;

import java.util.Random;

/**
 * In adapter: grows the per-block decoration rules and fills the bare chests and spawners the classic
 * SkyGrid algorithms place from the block table.
 *
 * It scans only the grid cells the algorithm can place a block on (x/z on grid lines, y on grid
 * layers), so the cost is a few hundred lookups per chunk, not a full volume scan. At each cell it
 * first tries the decoration rules ({@link RuleResolver#decorations()}): a rolled rule places its
 * {@link net.onelitefeather.skygridng.api.model.StructurePattern} (guaranteeing real solid support,
 * and any boxed water it declares), grows its plant column and applies its physics. If no decoration
 * lands, it fills an existing chest or spawner with the loot table or mob the core picks
 * (deterministic). Everything is seeded from the world seed and the cell coordinates, so the result
 * is independent of population order. Used only for the classic algorithms (WFC has its own
 * populator).
 */
public final class SkyGridDecorationPopulator extends BlockPopulator {

    private final SkyGridDecorations decorations;
    private final RuleResolver rules;

    public SkyGridDecorationPopulator(SkyGridDecorations decorations, RuleResolver rules) {
        this.decorations = decorations;
        this.rules = rules;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion area) {
        int spacing = decorations.spacing();
        long seed = worldInfo.getSeed();
        int minY = Math.max(decorations.minY(), worldInfo.getMinHeight());
        int maxY = Math.min(decorations.maxY(), worldInfo.getMaxHeight() - 1);

        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        for (int x = baseX; x < baseX + 16; x++) {
            if (Math.floorMod(x, spacing) != 0) {
                continue;
            }
            for (int z = baseZ; z < baseZ + 16; z++) {
                if (Math.floorMod(z, spacing) != 0) {
                    continue;
                }
                for (int y = firstGridLine(minY, spacing); y <= maxY; y += spacing) {
                    if (area.isInRegion(x, y, z)) {
                        decorate(area, seed, x, y, z);
                    }
                }
            }
        }
    }

    private void decorate(LimitedRegion area, long seed, int x, int y, int z) {
        if (placeDecoration(area, seed, x, y, z)) {
            return;
        }
        Material material = area.getType(x, y, z);
        if (material == Material.CHEST) {
            decorations.lootTableAt(seed, x, y, z).ifPresent(id -> {
                LootTable table = BukkitIds.lootTable(id);
                if (table != null && area.getBlockState(x, y, z) instanceof Chest chest) {
                    chest.setLootTable(table);
                    chest.setSeed(Seeds.blockSeed(seed, x, y, z));
                    chest.update(true, false);
                }
            });
        } else if (material == Material.SPAWNER) {
            decorations.spawnerMobAt(seed, x, y, z).ifPresent(id -> {
                EntityType mob = BukkitIds.entityType(id);
                if (mob != null && area.getBlockState(x, y, z) instanceof CreatureSpawner spawner) {
                    spawner.setSpawnedType(mob);
                    spawner.update(true, false);
                }
            });
        }
    }

    /**
     * Grows the first decoration rule that rolls a hit at this grid cell: it places the rule's
     * {@link net.onelitefeather.skygridng.api.model.StructurePattern} anchored at this cell (its solid
     * support, any boxed water and walls it declares), grows the plant column on top and schedules the
     * update the rule asks for. Returns {@code true} when a decoration was placed, so the caller does
     * not then treat the cell as a chest or spawner.
     */
    private boolean placeDecoration(LimitedRegion area, long seed, int x, int y, int z) {
        long base = Seeds.blockSeed(seed, x, y, z);
        for (Rule rule : rules.decorations()) {
            PlacementRule placement = rule.placement();
            long ruleSeed = base * 31 + rule.block().asString().hashCode();
            if (Seeds.unitDouble(ruleSeed) >= placement.probability()) {
                continue;
            }
            if (!StructurePlacer.place(area, rules, placement.structure(), x, y, z)) {
                return true; // rolled this rule but the structure does not fit here: consume the cell
            }
            growColumn(area, rule, placement, ruleSeed, x, y, z);
            return true;
        }
        return false;
    }

    private void growColumn(
            LimitedRegion area, Rule rule, PlacementRule placement, long ruleSeed, int x, int y, int z) {
        BlockData plant = rules.blockData(rule.block());
        int span = placement.maxHeight() - placement.minHeight() + 1;
        int height = placement.minHeight() + (int) (Seeds.unitDouble(ruleSeed * 7 + 13) * span);
        int placed = 0;
        for (int h = 1; h <= height; h++) {
            int py = y + h;
            if (!area.isInRegion(x, py, z)) {
                break;
            }
            area.setBlockData(x, py, z, plant);
            placed++;
        }
        if (placed > 0) {
            // Schedule the update on the base of the plant column: that is where the support and water
            // checks apply, and a block update there cascades up the rest of the column.
            RuleResolver.applyPhysics(area, rule.physics(), x, y + 1, z);
        }
    }

    private static int firstGridLine(int minY, int spacing) {
        int mod = Math.floorMod(minY, spacing);
        return mod == 0 ? minY : minY + (spacing - mod);
    }
}
