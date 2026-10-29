package net.onelitefeather.skygridng.generator;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;

/**
 * A platform-neutral biome identifier: a namespaced Minecraft id such as {@code minecraft:plains}.
 *
 * Mirrors {@link BlockId}: the core names biomes by this value, never by a Bukkit {@code Biome}
 * constant, so the biome map stays portable. A platform adapter resolves a {@code BiomeId} to its
 * native biome (the Paper adapter via {@code Registry.BIOME}). The same id works on Bukkit, Fabric,
 * Forge, and Sponge.
 *
 * <p>Parsing and character validation delegate to Adventure's {@link Key}, which every supported
 * platform already ships, instead of a hand-rolled regex. Implementing {@link Keyed} lets any
 * Adventure-aware code treat a {@code BiomeId} as a standard keyed resource.
 *
 * @param key the underlying namespace/value pair, lowercase
 */
public record BiomeId(Key key) implements Keyed {

    /** The default Minecraft namespace, used when {@link #of(String)} gets a bare id. */
    public static final String DEFAULT_NAMESPACE = Key.MINECRAFT_NAMESPACE;

    public BiomeId {
        Objects.requireNonNull(key, "BiomeId.key must not be null");
    }

    /** Parses a namespaced id; a bare {@code "plains"} defaults to {@link #DEFAULT_NAMESPACE}. */
    public static BiomeId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BiomeId must not be blank");
        }
        try {
            return new BiomeId(Key.key(id.trim().toLowerCase(Locale.ROOT)));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("BiomeId has invalid characters: " + id, e);
        }
    }

    /** The resource namespace, lowercase (for example {@code minecraft}). */
    public String namespace() {
        return key.namespace();
    }

    /** The resource path, lowercase (for example {@code snowy_plains}). */
    public String value() {
        return key.value();
    }

    /** The canonical string form {@code namespace:value}, for example {@code minecraft:plains}. */
    public String asString() {
        return key.asString();
    }

    @Override
    public String toString() {
        return asString();
    }
}
