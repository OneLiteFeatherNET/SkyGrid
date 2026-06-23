package net.onelitefeather.gridwfc.adapter.in.paper;

import org.bukkit.Material;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves material names (from the JSON configuration) to Bukkit {@link Material} and caches them.
 *
 * This Bukkit dependency lives in the adapter on purpose, not in the core. Unknown names map
 * defensively to a fallback block so a misconfiguration does not crash the server.
 */
final class MaterialResolver {

    private final ConcurrentHashMap<String, Material> cache = new ConcurrentHashMap<>();
    private final Material fallback;

    MaterialResolver(Material fallback) {
        this.fallback = fallback;
    }

    Material resolve(String name) {
        return cache.computeIfAbsent(name, key -> {
            Material material = Material.matchMaterial(key);
            return material != null ? material : fallback;
        });
    }
}
