package net.onelitefeather.skygridng.generator;

import java.util.Map;

/**
 * Read/write access to a single native generation region, addressed by world coordinates and
 * platform-neutral {@link BlockId}s. This is the seam that lets decoration, structure and
 * still-fluid logic (looking up a neighbor block, placing a block with rule state, scheduling a
 * physics update, or filling a chest or spawner) stay framework-free while it runs against a live
 * native region such as Paper's {@code LimitedRegion}. It is not implemented by {@code core}: the
 * region itself (bounds, chunk loading, native block state) only exists on the platform, so the
 * implementation lives in the composition root, wrapping the native region, and is passed into
 * generation code as a plain parameter, constructed fresh for each generation call, never held as a
 * long lived or injected singleton.
 */
public interface RegionWriter {

    /** Returns whether (x, y, z) lies within the region this writer can read and write. */
    boolean isInRegion(int x, int y, int z);

    /**
     * Reads the block currently at (x, y, z) as a platform-neutral namespaced id, for example
     * {@code minecraft:air} where a native reader would compare against an air material constant.
     */
    BlockId blockAt(int x, int y, int z);

    /** Places {@code block} at (x, y, z), with any rule-defined state applied at its default values. */
    void place(int x, int y, int z, BlockId block);

    /**
     * Places {@code block} at (x, y, z), with the given rule state (property name to value) applied
     * on top of its defaults.
     */
    void place(int x, int y, int z, BlockId block, Map<String, String> state);

    /**
     * Schedules the physics reaction for the block already placed at (x, y, z), per {@code mode}: a
     * block update, a fluid update, or none. See {@link PhysicsMode} for what each mode means.
     */
    void scheduleUpdate(int x, int y, int z, PhysicsMode mode);

    /**
     * Fills the container at (x, y, z) with the loot table named by {@code lootTable}, seeded with
     * {@code seed} so the roll is deterministic from the world seed.
     */
    void fillLootChest(int x, int y, int z, BlockId lootTable, long seed);

    /** Sets the spawner at (x, y, z) to spawn the entity type named by {@code mobType}. */
    void fillSpawner(int x, int y, int z, BlockId mobType);
}
