package net.onelitefeather.skygridng.api.model;

/**
 * A decoration placed on a tile column, modeled as a discriminated union: a decoration is exactly one
 * of {@link Plant}, {@link LootChest}, or {@link Spawner}.
 *
 * Consumers switch exhaustively over the permitted records (no {@code default}), so adding a new kind
 * forces every placement site to handle it - the compiler enforces completeness. Blocks, loot tables,
 * and mobs are platform-neutral {@link BlockId}s; the adapter resolves them.
 */
public sealed interface Decoration permits Plant, LootChest, Spawner {

    /** Spawn chance per eligible column, in [0,1]. */
    double probability();
}
