package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.Plant;

import java.util.Optional;

/**
 * In port: the deterministic block-entity decoration decisions for the classic SkyGrid grid.
 *
 * The classic algorithms place bare chests and spawners from the weighted block table; this catalog
 * decides what to put in them. The grid bounds let the adapter scan only the cells the classic
 * algorithm can place a block on. For a chest or spawner found there, the catalog picks a loot table
 * or a mob deterministically from the configured weighted lists (empty if none configured). The core
 * decides WHAT; the adapter applies the block-entity data, which only a populator can set.
 */
public interface SkyGridDecorations {

    /** True if anything is configured to decorate (loot tables or spawner mobs). */
    boolean active();

    /** The grid spacing, so the adapter scans only x/z on grid lines. */
    int spacing();

    /** The lowest grid layer that may hold a block. */
    int minY();

    /** The highest grid layer that may hold a block. */
    int maxY();

    /** The loot table to fill a chest at the given cell, deterministic, or empty if none configured. */
    Optional<BlockId> lootTableAt(long seed, int x, int y, int z);

    /** The mob for a spawner at the given cell, deterministic, or empty if none configured. */
    Optional<BlockId> spawnerMobAt(long seed, int x, int y, int z);

    /**
     * A self-contained plant to scatter at the given grid cell (deterministic, low chance), or empty.
     * The adapter places the plant's support, its own water source, and the plant column, so water and
     * sugar cane reliably appear on the sparse classic grid where blocks are never naturally adjacent.
     */
    Optional<Plant> plantAt(long seed, int x, int y, int z);
}
