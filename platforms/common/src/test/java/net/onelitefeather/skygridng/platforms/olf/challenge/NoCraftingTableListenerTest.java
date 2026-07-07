package net.onelitefeather.skygridng.platforms.olf.challenge;

import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MockBukkit + Mockito tests for the driving adapter: MockBukkit supplies the player, world and
 * block, Mockito mocks the {@link ChallengeAdminUseCase} port. The listener method is invoked
 * directly with a hand-built {@link PlayerInteractEvent}, which is enough to exercise its guard
 * chain without going through the plugin manager's event bus.
 */
class NoCraftingTableListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private ChallengeAdminUseCase challenges;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("skygrid-world");
        player = server.addPlayer();
        challenges = mock(ChallengeAdminUseCase.class);
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NoCraftingTableListener listener(Predicate<World> appliesTo) {
        return new NoCraftingTableListener(challenges, appliesTo, plugin);
    }

    private static boolean isRegistered(HandlerList handlerList, Object listenerInstance) {
        for (RegisteredListener registration : handlerList.getRegisteredListeners()) {
            if (registration.getListener() == listenerInstance) {
                return true;
            }
        }
        return false;
    }

    private PlayerInteractEvent interact(Player p, Block block, EquipmentSlot hand) {
        return new PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK, null, block,
                org.bukkit.block.BlockFace.UP, hand);
    }

    @Test
    @DisplayName("challenge inactive leaves the event untouched")
    void challengeInactiveLeavesEventUntouched() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(false);
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        PlayerInteractEvent event = interact(player, table, EquipmentSlot.HAND);

        listener(w -> true).onInteract(event);

        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.ALLOW);
    }

    @Test
    @DisplayName("challenge active, main hand, crafting table and matching world denies block use")
    void challengeActiveMainHandCraftingTableMatchingWorldDenies() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        PlayerInteractEvent event = interact(player, table, EquipmentSlot.HAND);

        listener(w -> true).onInteract(event);

        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.DENY);
    }

    @Test
    @DisplayName("an off-hand interaction is left untouched even when otherwise matching")
    void offHandLeftUntouched() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        PlayerInteractEvent event = interact(player, table, EquipmentSlot.OFF_HAND);

        listener(w -> true).onInteract(event);

        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.ALLOW);
    }

    @Test
    @DisplayName("a wrong block type is left untouched")
    void wrongBlockTypeLeftUntouched() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        Block stone = world.getBlockAt(0, 64, 0);
        stone.setType(Material.STONE);
        PlayerInteractEvent event = interact(player, stone, EquipmentSlot.HAND);

        listener(w -> true).onInteract(event);

        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.ALLOW);
    }

    @Test
    @DisplayName("a non-matching world is left untouched")
    void nonMatchingWorldLeftUntouched() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        PlayerInteractEvent event = interact(player, table, EquipmentSlot.HAND);

        listener(w -> false).onInteract(event);

        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.ALLOW);
    }

    @Test
    @DisplayName("challengeId returns the constant challenge id")
    void challengeIdReturnsConstant() {
        assertThat(listener(w -> true).challengeId()).isEqualTo(NoCraftingTableListener.CHALLENGE_ID);
    }

    @Test
    @DisplayName("enable registers this listener with the plugin manager")
    void enableRegistersListener() {
        NoCraftingTableListener behavior = listener(w -> true);

        behavior.enable();

        assertThat(isRegistered(PlayerInteractEvent.getHandlerList(), behavior)).isTrue();
    }

    @Test
    @DisplayName("disable unregisters this listener")
    void disableUnregistersListener() {
        NoCraftingTableListener behavior = listener(w -> true);
        behavior.enable();

        behavior.disable();

        assertThat(isRegistered(PlayerInteractEvent.getHandlerList(), behavior)).isFalse();
    }
}
