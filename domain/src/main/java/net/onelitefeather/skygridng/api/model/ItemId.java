package net.onelitefeather.skygridng.api.model;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.onelitefeather.skygridng.generator.BlockId;

/**
 * A platform-neutral item identifier: a namespaced Minecraft id such as {@code minecraft:diamond}.
 *
 * The core, the ports, and the public api name items only by this value, never by a Bukkit
 * {@code Material} enum name. A namespaced id is the same on every platform (Bukkit, Fabric, Forge,
 * Sponge) and on vanilla, so a {@link Reward.ItemGrant} stays portable. A platform adapter resolves
 * an {@code ItemId} to its native item type (the Paper adapter via {@code NamespacedKey} and
 * {@code Registry.ITEM}), mirroring {@link BlockId}'s WorldEdit-style resolution but for items.
 *
 * <p>Parsing and character validation delegate to Adventure's {@link Key}, which every supported
 * platform already ships, instead of a hand-rolled regex. Implementing {@link Keyed} lets any
 * Adventure-aware code treat an {@code ItemId} as a standard keyed resource.
 *
 * @param key the underlying namespace/value pair, lowercase
 */
public record ItemId(Key key) implements Keyed {

    /** The default Minecraft namespace, used when {@link #of(String)} gets a bare id. */
    public static final String DEFAULT_NAMESPACE = Key.MINECRAFT_NAMESPACE;

    public ItemId {
        Objects.requireNonNull(key, "ItemId.key must not be null");
    }

    /**
     * Parses a namespaced id. {@code "minecraft:diamond"} keeps its namespace; a bare
     * {@code "diamond"} defaults to {@link #DEFAULT_NAMESPACE}. The result is lowercased.
     */
    public static ItemId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ItemId must not be blank");
        }
        try {
            return new ItemId(Key.key(id.trim().toLowerCase(Locale.ROOT)));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("ItemId has invalid characters: " + id, e);
        }
    }

    /** The resource namespace, lowercase (for example {@code minecraft}). */
    public String namespace() {
        return key.namespace();
    }

    /** The resource path, lowercase (for example {@code diamond}). */
    public String value() {
        return key.value();
    }

    /** The canonical string form {@code namespace:value}, for example {@code minecraft:diamond}. */
    public String asString() {
        return key.asString();
    }

    @Override
    public String toString() {
        return asString();
    }
}
