package net.onelitefeather.skygridng.ports.algorithm;

import net.onelitefeather.skygridng.api.model.BlockId;
import java.util.OptionalInt;

/**
 * Read only access to a single native world's block state, addressed by world coordinates and
 * platform-neutral {@link BlockId}s. This is the seam that lets the first-join safe-spawn search
 * (finding the highest solid ground under a candidate column, then checking foot and head space
 * for a safe landing spot) stay framework-free while it runs against a live native world such as
 * Paper's {@code World}. It is not implemented by {@code core}: the world itself (loaded chunks,
 * heightmaps, native block state) only exists on the platform, so the implementation lives in the
 * composition root, wrapping the native world, and is passed into the search as a plain parameter,
 * constructed fresh for each first-join event, never held as a long lived or injected singleton,
 * since the specific world it wraps only exists on the platform and can change from one join to
 * the next.
 */
public interface GroundReader {

    /**
     * Returns the y of the highest solid-ish block at (x, z), using motion-blocking heightmap
     * semantics, or empty if the column is a void with no solid block at all.
     */
    OptionalInt highestGroundY(int x, int z);

    /** Reads the block at (x, y, z) as a platform-neutral namespaced id. */
    BlockId blockAt(int x, int y, int z);

    /** Returns whether the block at (x, y, z) is solid ground a player can stand on. */
    boolean isSolid(int x, int y, int z);

    /** Returns whether the block at (x, y, z) is passable, meaning an entity can occupy that space. */
    boolean isPassable(int x, int y, int z);
}
