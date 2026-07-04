package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.Decoration;
import net.onelitefeather.skygridng.api.model.GenerationSettings;
import net.onelitefeather.skygridng.api.model.LootChest;
import net.onelitefeather.skygridng.api.model.Plant;
import net.onelitefeather.skygridng.api.model.Spawner;
import net.onelitefeather.skygridng.api.model.TileType;
import net.onelitefeather.skygridng.ports.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.api.util.Seeds;
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
import java.util.random.RandomGenerator;

/**
 * In adapter: places the {@link Decoration}s defined on a WFC tile.
 *
 * Fully data driven: what spawns, how tall, and under which conditions comes from the configuration.
 * The adapter uses a per cell deterministic RNG so the result is independent of population order.
 * A {@link Plant} grows a column; a {@link LootChest} places a container and fills it from a loot
 * table; a {@link Spawner} places a spawner with a mob - block-entity data only a populator can set.
 * Only used when the WFC algorithm is active.
 */
public final class MaterialPopulator extends BlockPopulator {

    private static final int[][] NEIGHBORS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private final GenerateRegionUseCase region;
    private final RuleResolver rules;

    public MaterialPopulator(GenerateRegionUseCase region, RuleResolver rules) {
        this.region = region;
        this.rules = rules;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion area) {
        long seed = worldInfo.getSeed();
        GenerationSettings settings = region.rules().settings();
        int cellSize = settings.cellSize();
        int surfaceY = settings.surfaceY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX * 16 + x;
                int globalZ = chunkZ * 16 + z;
                int cellX = Math.floorDiv(globalX, cellSize);
                int cellZ = Math.floorDiv(globalZ, cellSize);
                TileType tile = region.tileAt(seed, cellX, cellZ);
                if (tile.features().isEmpty()) {
                    continue;
                }
                RandomGenerator rng = Seeds.rng(Seeds.cellSeed(seed, globalX, globalZ));
                for (Decoration decoration : tile.features()) {
                    place(area, decoration, globalX, surfaceY, globalZ, rng, seed);
                }
            }
        }
    }

    private void place(
            LimitedRegion area, Decoration decoration, int x, int surfaceY, int z, RandomGenerator rng, long seed) {
        if (rng.nextDouble() > decoration.probability()) {
            return;
        }
        if (!area.isInRegion(x, surfaceY + 1, z)) {
            return;
        }
        switch (decoration) {
            case Plant plant -> placePlant(area, plant, x, surfaceY, z, rng);
            case LootChest chest -> placeLootChest(area, chest, x, surfaceY, z, seed);
            case Spawner spawner -> placeSpawner(area, spawner, x, surfaceY, z);
        }
    }

    private void placePlant(LimitedRegion area, Plant plant, int x, int surfaceY, int z, RandomGenerator rng) {
        if (plant.requiresGroundBlock() != null) {
            Material ground = rules.material(plant.requiresGroundBlock());
            if (area.getType(x, surfaceY, z) != ground) {
                return;
            }
        }
        if (plant.providesWater()) {
            if (!StillWater.place(area, x, surfaceY, z)) {
                return; // no room for a contained pool here; skip the plant rather than leave it dry
            }
        } else if (plant.requiresLiquidNeighbor() && !hasLiquidNeighbor(area, x, surfaceY, z)) {
            return;
        }

        BlockData material = rules.blockData(plant.material());
        int height = plant.minHeight() + rng.nextInt(plant.maxHeight() - plant.minHeight() + 1);
        for (int h = 1; h <= height; h++) {
            int y = surfaceY + h;
            if (!area.isInRegion(x, y, z)) {
                break;
            }
            area.setBlockData(x, y, z, material);
        }
    }

    private void placeLootChest(LimitedRegion area, LootChest chest, int x, int surfaceY, int z, long seed) {
        int y = surfaceY + 1;
        area.setBlockData(x, y, z, rules.blockData(chest.block()));
        if (area.getBlockState(x, y, z) instanceof Chest container) {
            LootTable table = BukkitIds.lootTable(chest.lootTable());
            if (table != null) {
                container.setLootTable(table);
                container.setSeed(Seeds.blockSeed(seed, x, y, z));
                container.update(true, false);
            }
        }
    }

    private void placeSpawner(LimitedRegion area, Spawner spawner, int x, int surfaceY, int z) {
        int y = surfaceY + 1;
        area.setBlockData(x, y, z, rules.blockData(spawner.block()));
        if (area.getBlockState(x, y, z) instanceof CreatureSpawner placed) {
            EntityType mob = BukkitIds.entityType(spawner.mob());
            if (mob != null) {
                placed.setSpawnedType(mob);
                placed.update(true, false);
            }
        }
    }

    private boolean hasLiquidNeighbor(LimitedRegion area, int x, int y, int z) {
        for (int[] offset : NEIGHBORS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            if (area.isInRegion(nx, y, nz)) {
                Material type = area.getType(nx, y, nz);
                if (type == Material.WATER || type == Material.LAVA) {
                    return true;
                }
            }
        }
        return false;
    }
}
