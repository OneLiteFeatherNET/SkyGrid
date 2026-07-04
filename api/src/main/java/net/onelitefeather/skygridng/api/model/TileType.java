package net.onelitefeather.skygridng.api.model;

import java.util.List;

/**
 * A macro tile type of the WFC grid, fully data driven from JSON.
 *
 * Immutable and shared (Flyweight): one instance per type, referenced by all cells. Blocks are
 * platform-neutral {@link BlockId}s so the core stays portable.
 *
 * @param id       unique id (referenced by adjacency rules), for example "water"
 * @param weight   relative frequency and collapse weight (> 0)
 * @param neutral  broadly compatible, usable as a deterministic region border anchor
 * @param surface  surface block
 * @param fill     block below the surface
 * @param liquid   true if the surface is a liquid column up to sea level
 * @param features optional decorations: plants, loot chests, spawners ({@link Decoration})
 */
public record TileType(
        String id,
        double weight,
        boolean neutral,
        BlockId surface,
        BlockId fill,
        boolean liquid,
        List<Decoration> features
) {
    public TileType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("TileType.id must not be blank");
        }
        if (weight <= 0.0) {
            throw new IllegalArgumentException("TileType.weight must be > 0: " + id);
        }
        if (surface == null || fill == null) {
            throw new IllegalArgumentException("TileType.surface and fill must not be null: " + id);
        }
        features = features == null ? List.of() : List.copyOf(features);
    }

    /** Returns a copy with a changed weight (for in game edits, Memento friendly). */
    public TileType withWeight(double newWeight) {
        return new TileType(id, newWeight, neutral, surface, fill, liquid, features);
    }
}
