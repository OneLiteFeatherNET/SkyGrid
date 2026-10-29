package net.onelitefeather.skygridng.generator;


import java.util.List;

/**
 * Framework-free: grows the per-block decoration rules and fills the bare chests and spawners the
 * classic SkyGrid algorithms place from the block table, driven entirely through {@link RegionWriter}.
 *
 * It scans only the grid cells the algorithm can place a block on (x/z on grid lines, y on grid
 * layers), so the cost is a few hundred lookups per chunk, not a full volume scan. At each cell it
 * first tries the decoration rules ({@code decorationRules}): a rolled rule places its
 * {@link net.onelitefeather.skygridng.generator.StructurePattern} (guaranteeing real solid support,
 * and any boxed water it declares), grows its plant column and schedules the physics update it asks
 * for. If no decoration lands, it fills an existing chest or spawner with the loot table or mob the
 * core picks (deterministic). Everything is seeded from the world seed and the cell coordinates, so
 * the result is independent of population order.
 */
public final class DecorationEngine {

    private static final BlockId CHEST = BlockId.of("minecraft:chest");
    private static final BlockId SPAWNER = BlockId.of("minecraft:spawner");

    private final SkyGridDecorations decorations;
    private final List<Rule> decorationRules;

    public DecorationEngine(SkyGridDecorations decorations, List<Rule> decorationRules) {
        this.decorations = decorations;
        this.decorationRules = List.copyOf(decorationRules);
    }

    /**
     * Scans the grid cells of chunk ({@code chunkX}, {@code chunkZ}) between {@code minY} and
     * {@code maxY} (clamped to the configured decoration bounds) and decorates every eligible cell
     * through {@code writer}.
     */
    public void decorateChunk(long seed, int minY, int maxY, int chunkX, int chunkZ, RegionWriter writer) {
        int spacing = decorations.spacing();
        int lowY = Math.max(decorations.minY(), minY);
        int highY = Math.min(decorations.maxY(), maxY);

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
                for (int y = firstGridLine(lowY, spacing); y <= highY; y += spacing) {
                    if (writer.isInRegion(x, y, z)) {
                        decorate(writer, seed, x, y, z);
                    }
                }
            }
        }
    }

    private void decorate(RegionWriter writer, long seed, int x, int y, int z) {
        if (placeDecoration(writer, seed, x, y, z)) {
            return;
        }
        BlockId block = writer.blockAt(x, y, z);
        if (block.equals(CHEST)) {
            decorations.lootTableAt(seed, x, y, z).ifPresent(
                    id -> writer.fillLootChest(x, y, z, id, Seeds.blockSeed(seed, x, y, z)));
        } else if (block.equals(SPAWNER)) {
            decorations.spawnerMobAt(seed, x, y, z).ifPresent(id -> writer.fillSpawner(x, y, z, id));
        }
    }

    /**
     * Grows the first decoration rule that rolls a hit at this grid cell: it places the rule's
     * {@link net.onelitefeather.skygridng.generator.StructurePattern} anchored at this cell (its solid
     * support, any boxed water and walls it declares), grows the plant column on top and schedules the
     * update the rule asks for. Returns {@code true} when a decoration was placed, so the caller does
     * not then treat the cell as a chest or spawner.
     */
    private boolean placeDecoration(RegionWriter writer, long seed, int x, int y, int z) {
        long base = Seeds.blockSeed(seed, x, y, z);
        for (Rule rule : decorationRules) {
            PlacementRule placement = rule.placement();
            long ruleSeed = base * 31 + rule.block().asString().hashCode();
            if (Seeds.unitDouble(ruleSeed) >= placement.probability()) {
                continue;
            }
            if (!StructurePlacer.place(writer, placement.structure(), x, y, z)) {
                return true; // rolled this rule but the structure does not fit here: consume the cell
            }
            growColumn(writer, rule, placement, ruleSeed, x, y, z);
            return true;
        }
        return false;
    }

    private void growColumn(
            RegionWriter writer, Rule rule, PlacementRule placement, long ruleSeed, int x, int y, int z) {
        BlockId plant = rule.block();
        int span = placement.maxHeight() - placement.minHeight() + 1;
        int height = placement.minHeight() + (int) (Seeds.unitDouble(ruleSeed * 7 + 13) * span);
        int placed = 0;
        for (int h = 1; h <= height; h++) {
            int py = y + h;
            if (!writer.isInRegion(x, py, z)) {
                break;
            }
            writer.place(x, py, z, plant);
            placed++;
        }
        if (placed > 0) {
            // Schedule the update on the base of the plant column: that is where the support and water
            // checks apply, and a block update there cascades up the rest of the column.
            writer.scheduleUpdate(x, y + 1, z, rule.physics());
        }
    }

    private static int firstGridLine(int minY, int spacing) {
        int mod = Math.floorMod(minY, spacing);
        return mod == 0 ? minY : minY + (spacing - mod);
    }
}
