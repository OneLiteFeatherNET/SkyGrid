package net.onelitefeather.skygridng.challenge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a platform-neutral {@link ItemId} (for example {@code minecraft:diamond}) to a Bukkit
 * {@link Material} via the namespaced registry, and caches the result.
 *
 * Mirrors {@code adapters.generator.MaterialResolver}'s WorldEdit-style resolution: the core and the
 * ports speak namespaced ids, only this adapter looks them up against a Bukkit registry. Bukkit
 * unifies block and item types under the single {@link Material} enum, so this resolver deliberately
 * reuses {@link Registry#MATERIAL} (not a separate item registry) the same way
 * {@code MaterialResolver} does, just for reward-granting call sites instead of world generation.
 * Unknown ids map defensively to a fallback so a misconfigured reward does not crash the server.
 */
final class ItemResolver {

    private final ConcurrentHashMap<ItemId, Material> cache = new ConcurrentHashMap<>();
    private final Material fallback;

    ItemResolver(Material fallback) {
        this.fallback = fallback;
    }

    Material resolve(ItemId item) {
        return cache.computeIfAbsent(item, i -> {
            String id = i.asString();
            NamespacedKey key = NamespacedKey.fromString(id);
            Material material = key == null ? null : Registry.MATERIAL.get(key);
            if (material == null) {
                material = Material.matchMaterial(id); // robust fallback (also accepts namespaced ids)
            }
            return material != null ? material : fallback;
        });
    }
}
