package net.onelitefeather.skygridng.ports.algorithm;

import net.onelitefeather.skygridng.api.model.BlockId;

/**
 * Sink for blocks an algorithm wants to place in a single column.
 *
 * The core only emits (y, {@link BlockId}) pairs. The platform adapter implements the sink, resolves
 * the namespaced id to its native block, and writes it into the chunk. This keeps the algorithms free
 * of any platform type.
 */
@FunctionalInterface
public interface ColumnSink {

    /** Places a block at world height {@code y}, named by a platform-neutral namespaced id. */
    void place(int y, BlockId block);
}
