package net.onelitefeather.skygridng.api.model;

import java.util.List;
import java.util.Set;

/**
 * Precomputed adjacency as bitmasks over tile indices.
 *
 * A cell domain is a long bitmask (bit i means tile index i is possible), so at most 64 tile
 * types are supported. Propagation becomes pure bit logic.
 *
 * Currently direction independent (symmetric simple tiled model). The {@link Direction} stays in
 * the signature so direction dependent rules can be added later without an API break.
 */
public final class AdjacencyRules {

    public static final int MAX_TILES = 64;

    private final long[] allowedByIndex;

    private AdjacencyRules(long[] allowedByIndex) {
        this.allowedByIndex = allowedByIndex;
    }

    /**
     * Builds adjacency from the tile list and the allowed pairs.
     *
     * @param tiles     order defines the index and bit assignment
     * @param allowed   undirected allowed pairs (by id)
     * @param allowSelf if true, each tile may sit next to itself
     */
    public static AdjacencyRules from(List<TileType> tiles, Set<AllowedPair> allowed, boolean allowSelf) {
        if (tiles.size() > MAX_TILES) {
            throw new IllegalArgumentException("At most " + MAX_TILES + " tile types are supported");
        }
        long[] mask = new long[tiles.size()];
        if (allowSelf) {
            for (int i = 0; i < tiles.size(); i++) {
                mask[i] |= (1L << i);
            }
        }
        for (AllowedPair pair : allowed) {
            int ia = indexOf(tiles, pair.a());
            int ib = indexOf(tiles, pair.b());
            mask[ia] |= (1L << ib);
            mask[ib] |= (1L << ia);
        }
        return new AdjacencyRules(mask);
    }

    private static int indexOf(List<TileType> tiles, String id) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown tile id in adjacency rule: " + id);
    }

    /** Union of all allowed neighbors for every tile set in {@code domainMask}. */
    public long support(long domainMask, Direction direction) {
        long support = 0L;
        long m = domainMask;
        while (m != 0L) {
            int index = Long.numberOfTrailingZeros(m);
            support |= allowedByIndex[index];
            m &= (m - 1);
        }
        return support;
    }

    public int tileCount() {
        return allowedByIndex.length;
    }
}
