package net.onelitefeather.skygridng.generator;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.loot.LootTable;

/**
 * Resolves platform-neutral {@link BlockId}s to the Bukkit objects a decoration needs: a loot table
 * and an entity type. Keeping the namespaced-id round-trip here (the same pattern as
 * {@link MaterialResolver}) is what lets the core speak only ids while the adapter resolves them.
 */
final class BukkitIds {

    private BukkitIds() {
    }

    static LootTable lootTable(BlockId id) {
        NamespacedKey key = NamespacedKey.fromString(id.asString());
        return key == null ? null : Bukkit.getLootTable(key);
    }

    static EntityType entityType(BlockId id) {
        NamespacedKey key = NamespacedKey.fromString(id.asString());
        return key == null ? null : Registry.ENTITY_TYPE.get(key);
    }
}
