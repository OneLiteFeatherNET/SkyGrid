package net.onelitefeather.skygridng.core.algorithm;

/**
 * Sink for blocks an algorithm wants to place in a single column.
 *
 * The core only emits (y, material name) pairs. The Paper adapter implements the sink and writes
 * the resolved Material into the chunk. This keeps the algorithms free of Bukkit.
 */
@FunctionalInterface
public interface ColumnSink {

    /** Places a block at world height {@code y}. Material is a Bukkit name, resolved by the adapter. */
    void place(int y, String material);
}
