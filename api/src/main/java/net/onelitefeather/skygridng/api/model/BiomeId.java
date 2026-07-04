package net.onelitefeather.skygridng.api.model;

import java.util.Locale;

/**
 * A platform-neutral biome identifier: a namespaced Minecraft id such as {@code minecraft:plains}.
 *
 * Mirrors {@link BlockId}: the core names biomes by this value, never by a Bukkit {@code Biome}
 * constant, so the biome map stays portable. A platform adapter resolves a {@code BiomeId} to its
 * native biome (the Paper adapter via {@code Registry.BIOME}). The same id works on Bukkit, Fabric,
 * Forge, and Sponge.
 *
 * @param namespace the resource namespace, lowercase (defaults to {@code minecraft})
 * @param value     the resource path, lowercase (for example {@code snowy_plains})
 */
public record BiomeId(String namespace, String value) {

    /** The default Minecraft namespace, used when {@link #of(String)} gets a bare id. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    public BiomeId {
        namespace = normalize(namespace, "namespace");
        value = normalize(value, "value");
    }

    /** Parses a namespaced id; a bare {@code "plains"} defaults to {@link #DEFAULT_NAMESPACE}. */
    public static BiomeId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BiomeId must not be blank");
        }
        int colon = id.indexOf(':');
        if (colon < 0) {
            return new BiomeId(DEFAULT_NAMESPACE, id);
        }
        return new BiomeId(id.substring(0, colon), id.substring(colon + 1));
    }

    /** The canonical string form {@code namespace:value}, for example {@code minecraft:plains}. */
    public String asString() {
        return namespace + ":" + value;
    }

    @Override
    public String toString() {
        return asString();
    }

    private static String normalize(String part, String what) {
        if (part == null) {
            throw new IllegalArgumentException("BiomeId." + what + " must not be null");
        }
        String trimmed = part.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("BiomeId." + what + " must not be blank");
        }
        if (!trimmed.matches("[a-z0-9_.\\-/]+")) {
            throw new IllegalArgumentException("BiomeId." + what + " has invalid characters: " + part);
        }
        return trimmed;
    }
}
