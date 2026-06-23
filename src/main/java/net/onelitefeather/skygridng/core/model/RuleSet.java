package net.onelitefeather.skygridng.core.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable aggregate of the whole WFC configuration.
 *
 * Also acts as a Memento: every in game change produces a new RuleSet, the old one stays for
 * undo. Because the record is immutable it is safe to share across threads.
 *
 * The tile order in {@code tiles} defines the bit and index assignment of the adjacency.
 */
public record RuleSet(
        List<TileType> tiles,
        Set<AllowedPair> allowed,
        GenerationSettings settings
) {
    public RuleSet {
        if (tiles == null || tiles.isEmpty()) {
            throw new IllegalArgumentException("RuleSet needs at least one tile");
        }
        if (tiles.size() > AdjacencyRules.MAX_TILES) {
            throw new IllegalArgumentException("At most " + AdjacencyRules.MAX_TILES + " tile types");
        }
        tiles = List.copyOf(tiles);
        allowed = Set.copyOf(allowed == null ? Set.of() : allowed);
        if (tileIndexOrMinusOne(tiles, settings.fallbackTileId()) < 0) {
            throw new IllegalArgumentException("fallbackTileId points to unknown tile: "
                    + settings.fallbackTileId());
        }
    }

    /** Builds the adjacency bitmasks (cache once per RuleSet on the caller side). */
    public AdjacencyRules buildAdjacency() {
        return AdjacencyRules.from(tiles, allowed, settings.allowSelf());
    }

    public Optional<TileType> tileById(String id) {
        return tiles.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public int tileIndex(String id) {
        int idx = tileIndexOrMinusOne(tiles, id);
        if (idx < 0) {
            throw new IllegalArgumentException("Unknown tile id: " + id);
        }
        return idx;
    }

    public List<String> tileIds() {
        return tiles.stream().map(TileType::id).toList();
    }

    /** Bitmask with all tiles (full superposition). */
    public long fullMask() {
        return tiles.size() == 64 ? -1L : (1L << tiles.size()) - 1L;
    }

    /** Bitmask of the neutral tiles (for region borders). */
    public long neutralMask() {
        long mask = 0L;
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).neutral()) {
                mask |= (1L << i);
            }
        }
        return mask == 0L ? fullMask() : mask;
    }

    // ----- Memento friendly edit operations (return new RuleSets) -----

    public RuleSet withTileWeight(String id, double weight) {
        List<TileType> updated = tiles.stream()
                .map(t -> t.id().equals(id) ? t.withWeight(weight) : t)
                .toList();
        return new RuleSet(updated, allowed, settings);
    }

    public RuleSet withAllowed(String a, String b) {
        tileIndex(a); // validates
        tileIndex(b);
        Set<AllowedPair> next = new LinkedHashSet<>(allowed);
        next.add(new AllowedPair(a, b));
        return new RuleSet(tiles, next, settings);
    }

    public RuleSet withoutAllowed(String a, String b) {
        Set<AllowedPair> next = new LinkedHashSet<>(allowed);
        next.removeIf(p -> (p.a().equals(a) && p.b().equals(b)) || (p.a().equals(b) && p.b().equals(a)));
        return new RuleSet(tiles, next, settings);
    }

    private static int tileIndexOrMinusOne(List<TileType> tiles, String id) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
