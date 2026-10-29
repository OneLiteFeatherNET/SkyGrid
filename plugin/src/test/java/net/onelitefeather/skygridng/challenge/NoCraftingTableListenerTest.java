package net.onelitefeather.skygridng.challenge;

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
import org.mockito.ArgumentCaptor;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MockBukkit + Mockito tests for the driving adapter: MockBukkit supplies the player, world and
 * block, Mockito mocks the {@link ChallengeAdminUseCase} and {@link TaskProgressUseCase} ports. The
 * listener method is invoked directly with a hand-built {@link PlayerInteractEvent}, which is enough
 * to exercise its guard chain without going through the plugin manager's event bus. The catalog
 * change listener registered in {@link NoCraftingTableListener#enable()} is captured with an {@link
 * ArgumentCaptor}, the same pattern {@link OneLifeListenerTest} uses.
 */
class NoCraftingTableListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private ChallengeAdminUseCase challenges;
    private TaskProgressUseCase taskProgress;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("skygrid-world");
        player = server.addPlayer();
        challenges = mock(ChallengeAdminUseCase.class);
        taskProgress = mock(TaskProgressUseCase.class);
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NoCraftingTableListener listener(Predicate<World> appliesTo) {
        return new NoCraftingTableListener(challenges, taskProgress, appliesTo, plugin);
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

    /**
     * Registers {@code listener} and captures the {@link Runnable} it passed to
     * {@link ChallengeAdminUseCase#addChangeListener}, so a test can invoke it directly to simulate
     * a catalog change notification without a real {@code ChallengeState}.
     */
    private Runnable enableAndCaptureCatalogListener(NoCraftingTableListener listener) {
        listener.enable();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(challenges).addChangeListener(captor.capture());
        return captor.getValue();
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

    // ----- round completion on the active-to-inactive transition -----

    @Test
    @DisplayName("an active-to-inactive transition completes the round task for every online player in a "
            + "matching world who never had an interaction denied, but not for one who did")
    void activeToInactiveTransitionCompletesTaskForNonViolatorsOnly() {
        PlayerMock violator = server.addPlayer();
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        NoCraftingTableListener listener = listener(w -> true);
        Runnable onCatalogChanged = enableAndCaptureCatalogListener(listener);
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        listener.onInteract(interact(violator, table, EquipmentSlot.HAND));

        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(false);
        onCatalogChanged.run();

        verify(taskProgress).completeTask(player.getUniqueId(), NoCraftingTableListener.CHALLENGE_ID,
                "no_crafting_table_round");
        verify(taskProgress, never()).completeTask(violator.getUniqueId(), NoCraftingTableListener.CHALLENGE_ID,
                "no_crafting_table_round");
    }

    @Test
    @DisplayName("a player in a non-matching world is not swept into completion on the active-to-inactive transition")
    void nonMatchingWorldPlayerNotSweptOnTransition() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        NoCraftingTableListener listener = listener(w -> false);
        Runnable onCatalogChanged = enableAndCaptureCatalogListener(listener);

        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(false);
        onCatalogChanged.run();

        verifyNoInteractions(taskProgress);
    }

    @Test
    @DisplayName("after a round completes, the violated set is cleared so a later round can complete the task again")
    void completedRoundClearsTheViolatedSet() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        NoCraftingTableListener listener = listener(w -> true);
        Runnable onCatalogChanged = enableAndCaptureCatalogListener(listener);
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.CRAFTING_TABLE);
        listener.onInteract(interact(player, table, EquipmentSlot.HAND));

        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(false);
        onCatalogChanged.run(); // player violated this round, so not completed
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        onCatalogChanged.run();
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(false);
        onCatalogChanged.run(); // next round, no violation this time

        verify(taskProgress).completeTask(player.getUniqueId(), NoCraftingTableListener.CHALLENGE_ID,
                "no_crafting_table_round");
    }

    @Test
    @DisplayName("an inactive-to-active transition does not trigger a completion sweep")
    void inactiveToActiveTransitionDoesNotSweep() {
        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(false);
        NoCraftingTableListener listener = listener(w -> true);
        Runnable onCatalogChanged = enableAndCaptureCatalogListener(listener);

        when(challenges.isActive(NoCraftingTableListener.CHALLENGE_ID)).thenReturn(true);
        onCatalogChanged.run();

        verifyNoInteractions(taskProgress);
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
