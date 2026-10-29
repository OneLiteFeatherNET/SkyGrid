package net.onelitefeather.skygridng.platforms.common.generator;

import net.onelitefeather.skygridng.api.model.BlockId;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a platform-neutral {@link BlockId} (for example {@code minecraft:stone}) to a Bukkit
 * {@link Material} via the namespaced registry, and caches the result.
 *
 * This is the only place the Bukkit vocabulary appears: the core speaks namespaced ids, the adapter
 * looks them up against {@code Registry.MATERIAL}. A Fabric or Forge adapter would resolve the same
 * id against its own registry. Unknown ids map defensively to a fallback so a misconfiguration does
 * not crash the server.
 */
final class MaterialResolver {

    private final ConcurrentHashMap<BlockId, Material> cache = new ConcurrentHashMap<>();
    private final Material fallback;

    MaterialResolver(Material fallback) {
        this.fallback = fallback;
    }

    Material resolve(BlockId block) {
        return cache.computeIfAbsent(block, b -> {
            String id = b.asString();
            NamespacedKey key = NamespacedKey.fromString(id);
            Material material = key == null ? null : Registry.MATERIAL.get(key);
            if (material == null) {
                material = Material.matchMaterial(id); // robust fallback (also accepts namespaced ids)
            }
            return material != null ? material : fallback;
        });
    }
}
