package net.onelitefeather.skygridng.generator;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Driving adapter: places first-join players near their own spread-spawn slot via the
 * {@link SpawnPoints} in-port.
 *
 * Only the very first join into a SkyGrid world is redirected: the player is assigned the next
 * spread slot (a counter kept in the world's persistent data), then {@link SafeLocationFinder}
 * searches outward from that slot for a safe, standable block (a SkyBlock/EssentialsX-RTP style
 * search, since a sparse SkyGrid world rarely has solid ground exactly at the slot), and an ordinal
 * marker is written to the player's persistent data. Every later join is left untouched, so bed and
 * logout positions keep working. The slot layout lives in the core; this class only talks to Bukkit.
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
        RandomGenerator rng = Seeds.rng(Seeds.mix(world.getSeed(), slot.x(), slot.z()));
        BukkitGroundReader ground = new BukkitGroundReader(world);
        Optional<SafeLocationFinder.Spot> spot = SafeLocationFinder.find(
                ground, slot.x(), slot.z(), spawnPoints.searchRadius(), spawnPoints.maxAttempts(), rng);
        Location location = spot
                .map(s -> new Location(world, s.x() + 0.5, s.y(), s.z() + 0.5))
                .orElseGet(() -> fallback(world, slot));
        playerData.set(ordinalKey, PersistentDataType.INTEGER, ordinal);
        event.setSpawnLocation(location);
    }

    /** A fixed high point above the slot center; used only when every safe-block probe fails. */
    private Location fallback(World world, SpawnPoint slot) {
        return new Location(world, slot.x() + 0.5, world.getMaxHeight() - 1.0, slot.z() + 0.5);
    }

    /** Reads and advances the per-world join counter kept in the world's persistent data. */
    private int nextOrdinal(World world) {
        PersistentDataContainer worldData = world.getPersistentDataContainer();
        int next = worldData.getOrDefault(counterKey, PersistentDataType.INTEGER, 0);
        worldData.set(counterKey, PersistentDataType.INTEGER, next + 1);
        return next;
    }
}
