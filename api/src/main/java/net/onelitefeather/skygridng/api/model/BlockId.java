package net.onelitefeather.skygridng.api.model;

import java.util.Locale;

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
 * @param namespace the resource namespace, lowercase (defaults to {@code minecraft})
 * @param value     the resource path, lowercase (for example {@code grass_block})
 */
public record BlockId(String namespace, String value) {

    /** The default Minecraft namespace, used when {@link #of(String)} gets a bare id. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    public BlockId {
        namespace = normalize(namespace, "namespace");
        value = normalize(value, "value");
    }

    /**
     * Parses a namespaced id. {@code "minecraft:stone"} keeps its namespace; a bare {@code "stone"}
     * defaults to {@link #DEFAULT_NAMESPACE}. The result is lowercased.
     */
    public static BlockId of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("BlockId must not be blank");
        }
        int colon = id.indexOf(':');
        if (colon < 0) {
            return new BlockId(DEFAULT_NAMESPACE, id);
        }
        return new BlockId(id.substring(0, colon), id.substring(colon + 1));
    }

    /** The canonical string form {@code namespace:value}, for example {@code minecraft:stone}. */
    public String asString() {
        return namespace + ":" + value;
    }

    @Override
    public String toString() {
        return asString();
    }

    private static String normalize(String part, String what) {
        if (part == null) {
            throw new IllegalArgumentException("BlockId." + what + " must not be null");
        }
        String trimmed = part.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("BlockId." + what + " must not be blank");
        }
        if (!trimmed.matches("[a-z0-9_.\\-/]+")) {
            throw new IllegalArgumentException(
                    "BlockId." + what + " has invalid characters: " + part);
        }
        return trimmed;
    }
}
