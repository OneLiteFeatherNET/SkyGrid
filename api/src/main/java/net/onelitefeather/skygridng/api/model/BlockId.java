package net.onelitefeather.skygridng.api.model;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;

/**
 * A platform-neutral block identifier: a namespaced Minecraft id such as {@code minecraft:stone}.
 *
 * The core, the ports, and the public api name blocks only by this value, never by a Bukkit
 * {@code Material} enum name. A namespaced id is the same on every platform (Bukkit, Fabric, Forge,
 * Sponge) and on vanilla, so the generation logic stays portable. A platform adapter resolves a
 * {@code BlockId} to its native block type (the Paper adapter via {@code NamespacedKey} and
 * {@code Registry.MATERIAL}). This mirrors WorldEdit's {@code BlockType}, which is also keyed by a
 * namespaced id.
 *
 * <p>Parsing and character validation delegate to Adventure's {@link Key}, which every supported
 * platform already ships, instead of a hand-rolled regex. Implementing {@link Keyed} lets any
 * Adventure-aware code treat a {@code BlockId} as a standard keyed resource.
 *
 * @param key the underlying namespace/value pair, lowercase
 */
public record BlockId(Key key) implements Keyed {

    /** The default Minecraft namespace, used when {@link #of(String)} gets a bare id. */
    public static final String DEFAULT_NAMESPACE = Key.MINECRAFT_NAMESPACE;

    public BlockId {
        Objects.requireNonNull(key, "BlockId.key must not be null");
    }

    /**
     * Parses a namespaced id. {@code "minecraft:stone"} keeps its namespace; a bare {@code "stone"}
     * defaults to {@link #DEFAULT_NAMESPACE}. The result is lowercased.
     */
    public static BlockId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BlockId must not be blank");
        }
        try {
            return new BlockId(Key.key(id.trim().toLowerCase(Locale.ROOT)));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("BlockId has invalid characters: " + id, e);
        }
    }

    /** The resource namespace, lowercase (for example {@code minecraft}). */
    public String namespace() {
        return key.namespace();
    }

    /** The resource path, lowercase (for example {@code grass_block}). */
    public String value() {
        return key.value();
    }

    /** The canonical string form {@code namespace:value}, for example {@code minecraft:stone}. */
    public String asString() {
        return key.asString();
    }

    @Override
    public String toString() {
        return asString();
    }
}
