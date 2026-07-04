package net.onelitefeather.skygridng.adapters.spawn;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.SpawnPoint;
import net.onelitefeather.skygridng.ports.in.SpawnPoints;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.function.Predicate;

/**
 * Driving adapter: places first-join players on their own spread-spawn platform via the
 * {@link SpawnPoints} in-port.
 *
 * Only the very first join into a SkyGrid world is redirected: the player is assigned the next
 * spiral slot (a counter kept in the world's persistent data), a small starter platform is built so
 * they do not fall into the void, and an ordinal marker is written to the player's persistent data.
 * Every later join is left untouched, so bed and logout positions keep working. The slot layout
 * lives in the core; this class only talks to Bukkit.
 */
public final class SpreadSpawnListener implements Listener {

    private final SpawnPoints spawnPoints;
    private final Predicate<World> appliesTo;
    private final NamespacedKey ordinalKey;
    private final NamespacedKey counterKey;

    public SpreadSpawnListener(Plugin plugin, SpawnPoints spawnPoints, Predicate<World> appliesTo) {
        this.spawnPoints = spawnPoints;
        this.appliesTo = appliesTo;
        this.ordinalKey = new NamespacedKey(plugin, "spawn_ordinal");
        this.counterKey = new NamespacedKey(plugin, "spawn_counter");
    }

    @EventHandler
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        if (!spawnPoints.spread()) {
            return;
        }
        Player player = event.getPlayer();
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (playerData.has(ordinalKey, PersistentDataType.INTEGER)) {
            return; // already placed once; leave the saved/bed spawn alone
        }
        World world = event.getSpawnLocation().getWorld();
        if (world == null || !appliesTo.test(world)) {
            return; // not one of our worlds
        }

        int ordinal = nextOrdinal(world);
        SpawnPoint slot = spawnPoints.at(ordinal);
        buildPlatform(world, slot.x(), slot.z());
        if (spawnPoints.spawnVillager()) {
            placeVillager(world, slot.x(), slot.z());
        }
        playerData.set(ordinalKey, PersistentDataType.INTEGER, ordinal);

        int y = spawnPoints.platformY() + 1;
        event.setSpawnLocation(new Location(world, slot.x() + 0.5, y, slot.z() + 0.5));
    }

    /** Spawns a starter villager beside the platform centre, so trading is reachable from the start. */
    private void placeVillager(World world, int centerX, int centerZ) {
        int edge = spawnPoints.platformRadius();
        Location location = new Location(world, centerX + edge + 0.5, spawnPoints.platformY() + 1, centerZ + 0.5);
        world.spawnEntity(location, EntityType.VILLAGER);
    }

    /** Reads and advances the per-world join counter kept in the world's persistent data. */
    private int nextOrdinal(World world) {
        PersistentDataContainer worldData = world.getPersistentDataContainer();
        int next = worldData.getOrDefault(counterKey, PersistentDataType.INTEGER, 0);
        worldData.set(counterKey, PersistentDataType.INTEGER, next + 1);
        return next;
    }

    private void buildPlatform(World world, int centerX, int centerZ) {
        Material material = material(spawnPoints.platformBlock());
        int y = spawnPoints.platformY();
        int radius = spawnPoints.platformRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.getBlockAt(centerX + dx, y, centerZ + dz).setType(material, false);
            }
        }
    }

    private static Material material(BlockId block) {
        Material material = Material.matchMaterial(block.asString());
        return material != null && material.isBlock() ? material : Material.COBBLESTONE;
    }
}
