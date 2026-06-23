package net.onelitefeather.gridwfc.core.model;

import java.util.List;

/**
 * A macro tile type of the WFC grid, fully data driven from JSON.
 *
 * Immutable and shared (Flyweight): one instance per type, referenced by all cells. Material
 * names are strings so the core stays free of Bukkit.
 *
 * @param id       unique id (referenced by adjacency rules), for example "water"
 * @param weight   relative frequency and collapse weight (> 0)
 * @param neutral  broadly compatible, usable as a deterministic region border anchor
 * @param surface  surface block name
 * @param fill     block name below the surface
 * @param liquid   true if the surface is a liquid column up to sea level
 * @param features optional decorations (sugar cane, cactus, flowers, ...)
 */
public record TileType(
        String id,
        double weight,
        boolean neutral,
        String surface,
        String fill,
        boolean liquid,
        List<FeatureSpec> features
) {
    public TileType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("TileType.id must not be blank");
        }
        if (weight <= 0.0) {
            throw new IllegalArgumentException("TileType.weight must be > 0: " + id);
        }
        features = features == null ? List.of() : List.copyOf(features);
    }

    /** Returns a copy with a changed weight (for in game edits, Memento friendly). */
    public TileType withWeight(double newWeight) {
        return new TileType(id, newWeight, neutral, surface, fill, liquid, features);
    }
}
