package net.onelitefeather.skygridng.platforms.common.spawn;

import net.onelitefeather.skygridng.api.model.SpawnPoint;
import net.onelitefeather.skygridng.ports.in.SpawnPoints;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.persistence.PersistentDataContainerMock;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Black-box tests for the first-join spread-spawn listener, against a Mockito {@link World} whose
 * every column has a uniform, solid, safe floor: the safe-block search always succeeds on attempt 0,
 * so the resulting location is fully deterministic no matter which slot a player is assigned. The
 * player and the plugin come from MockBukkit, which gives a real persistent data container.
 */
class SpreadSpawnListenerTest {

    private static final int GROUND_Y = 4;

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Every column: solid, safe ground at {@link #GROUND_Y}, open air for the two blocks above it. */
    private World uniformFloorWorld() {
        World world = mock(World.class);
        when(world.getPersistentDataContainer()).thenReturn(new PersistentDataContainerMock());
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(64);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING))).thenReturn(GROUND_Y);
        when(world.getBlockAt(anyInt(), eq(GROUND_Y), anyInt())).thenAnswer(inv -> block(Material.STONE, false));
        when(world.getBlockAt(anyInt(), eq(GROUND_Y + 1), anyInt())).thenAnswer(inv -> block(Material.AIR, true));
        when(world.getBlockAt(anyInt(), eq(GROUND_Y + 2), anyInt())).thenAnswer(inv -> block(Material.AIR, true));
        return world;
    }

    private Block block(Material material, boolean passable) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(passable);
        return block;
    }

    private SpawnPoints spawnPoints(boolean spread) {
        SpawnPoints spawnPoints = mock(SpawnPoints.class);
        when(spawnPoints.spread()).thenReturn(spread);
        when(spawnPoints.searchRadius()).thenReturn(8);
        when(spawnPoints.maxAttempts()).thenReturn(4);
        return spawnPoints;
    }

    private NamespacedKey ordinalKey() {
        return new NamespacedKey(plugin, "spawn_ordinal");
    }

    private NamespacedKey counterKey() {
        return new NamespacedKey(plugin, "spawn_counter");
    }

    @Test
    @DisplayName("first join places the player at the safe block found near the assigned slot")
    void firstJoinPlacesPlayerNearSlot() {
        World world = uniformFloorWorld();
        SpawnPoints spawnPoints = spawnPoints(true);
        when(spawnPoints.at(0)).thenReturn(new SpawnPoint(100, 200));
        SpreadSpawnListener listener = new SpreadSpawnListener(plugin, spawnPoints, w -> true);
        PlayerMock player = server.addPlayer();
        PlayerSpawnLocationEvent event = new PlayerSpawnLocationEvent(player, new Location(world, 0, 0, 0));

        listener.onSpawnLocation(event);

        assertThat(event.getSpawnLocation()).isEqualTo(new Location(world, 100.5, GROUND_Y + 1.0, 200.5));
        assertThat(player.getPersistentDataContainer().has(ordinalKey(), PersistentDataType.INTEGER)).isTrue();
        assertThat(player.getPersistentDataContainer().get(ordinalKey(), PersistentDataType.INTEGER)).isEqualTo(0);
    }

    @Test
    @DisplayName("a second join for the same player is a no-op")
    void secondJoinIsNoOp() {
        World world = uniformFloorWorld();
        SpawnPoints spawnPoints = spawnPoints(true);
        when(spawnPoints.at(0)).thenReturn(new SpawnPoint(100, 200));
        SpreadSpawnListener listener = new SpreadSpawnListener(plugin, spawnPoints, w -> true);
        PlayerMock player = server.addPlayer();
        listener.onSpawnLocation(new PlayerSpawnLocationEvent(player, new Location(world, 0, 0, 0)));

        Location unrelated = new Location(world, 42, 42, 42);
        PlayerSpawnLocationEvent secondJoin = new PlayerSpawnLocationEvent(player, unrelated);
        listener.onSpawnLocation(secondJoin);

        assertThat(secondJoin.getSpawnLocation()).isEqualTo(unrelated);
        verify(spawnPoints, times(1)).at(anyInt());
    }

    @Test
    @DisplayName("spread() == false leaves the event untouched and never assigns a slot")
    void spreadDisabledIsNoOp() {
        World world = uniformFloorWorld();
        SpawnPoints spawnPoints = spawnPoints(false);
        SpreadSpawnListener listener = new SpreadSpawnListener(plugin, spawnPoints, w -> true);
        PlayerMock player = server.addPlayer();
        Location untouched = new Location(world, 7, 7, 7);
        PlayerSpawnLocationEvent event = new PlayerSpawnLocationEvent(player, untouched);

        listener.onSpawnLocation(event);

        assertThat(event.getSpawnLocation()).isEqualTo(untouched);
        assertThat(player.getPersistentDataContainer().has(ordinalKey(), PersistentDataType.INTEGER)).isFalse();
        verify(spawnPoints, never()).at(anyInt());
        assertThat(world.getPersistentDataContainer().has(counterKey(), PersistentDataType.INTEGER)).isFalse();
    }

    @Test
    @DisplayName("distinct players joining the same world get consecutive ordinals, in join order")
    void perWorldOrdinalIncrementsAcrossDistinctPlayers() {
        World world = uniformFloorWorld();
        SpawnPoints spawnPoints = spawnPoints(true);
        when(spawnPoints.at(anyInt())).thenAnswer(inv -> new SpawnPoint((int) inv.getArgument(0) * 1000, 0));
        SpreadSpawnListener listener = new SpreadSpawnListener(plugin, spawnPoints, w -> true);

        for (int i = 0; i < 3; i++) {
            PlayerMock player = server.addPlayer("player" + i);
            listener.onSpawnLocation(new PlayerSpawnLocationEvent(player, new Location(world, 0, 0, 0)));
        }

        verify(spawnPoints).at(0);
        verify(spawnPoints).at(1);
        verify(spawnPoints).at(2);
    }
}
