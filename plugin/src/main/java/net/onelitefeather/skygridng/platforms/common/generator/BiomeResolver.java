package net.onelitefeather.skygridng.platforms.common.generator;

import net.onelitefeather.skygridng.api.model.BiomeId;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a platform-neutral {@link BiomeId} (for example {@code minecraft:plains}) to a Bukkit
 * {@link Biome} via the namespaced registry, and caches the result. The same id-to-registry round
 * trip as {@link MaterialResolver}; a Fabric or Forge adapter would resolve it against its own
 * registry. Unknown ids map to a fallback so a bad palette entry does not break world load.
 */
final class BiomeResolver {

    private final ConcurrentHashMap<BiomeId, Biome> cache = new ConcurrentHashMap<>();
    private final Biome fallback;

    BiomeResolver(Biome fallback) {
        this.fallback = fallback;
    }

    Biome resolve(BiomeId id) {
        return cache.computeIfAbsent(id, biomeId -> {
            NamespacedKey key = NamespacedKey.fromString(biomeId.asString());
            Biome biome = key == null ? null : Registry.BIOME.get(key);
            return biome != null ? biome : fallback;
        });
    }
}
