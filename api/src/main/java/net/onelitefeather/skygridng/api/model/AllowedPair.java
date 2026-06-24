package net.onelitefeather.skygridng.api.model;

/** An undirected allowed neighborship between two tiles (by id). */
public record AllowedPair(String a, String b) {
    public AllowedPair {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            throw new IllegalArgumentException("AllowedPair needs two ids");
        }
    }
}
