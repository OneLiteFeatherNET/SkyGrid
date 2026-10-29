package net.onelitefeather.skygridng.challenge;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityRegainHealthEvent;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MockBukkit + Mockito tests for the driving adapter: MockBukkit supplies the player, world and a
 * non-player entity, Mockito mocks the {@link ChallengeAdminUseCase} and {@link TaskProgressUseCase}
 * ports. The listener method is invoked directly with a hand-built {@link EntityRegainHealthEvent}.
 * The catalog change listener registered in {@link NoNaturalRegenerationListener#enable()} is
 * captured with an {@link ArgumentCaptor}, the same pattern {@link OneLifeListenerTest} uses.
 */
class NoNaturalRegenerationListenerTest {

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

    private NoNaturalRegenerationListener listener(Predicate<World> appliesTo) {
        return new NoNaturalRegenerationListener(challenges, taskProgress, appliesTo, plugin);
    }

    private static boolean isRegistered(HandlerList handlerList, Object listenerInstance) {
        for (RegisteredListener registration : handlerList.getRegisteredListeners()) {
            if (registration.getListener() == listenerInstance) {
                return true;
            }
        }
        return false;
    }

    private EntityRegainHealthEvent regain(Entity entity, EntityRegainHealthEvent.RegainReason reason) {
        return new EntityRegainHealthEvent(entity, 1.0, reason);
    }

    /**
     * Registers {@code listener} and captures the {@link Runnable} it passed to
     * {@link ChallengeAdminUseCase#addChangeListener}, so a test can invoke it directly to simulate
     * a catalog change notification without a real {@code ChallengeState}.
     */
    private Runnable enableAndCaptureCatalogListener(NoNaturalRegenerationListener listener) {
        listener.enable();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(challenges).addChangeListener(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("SATIATED regen is cancelled while active and world matches")
    void satiatedCancelledWhileActiveAndMatching() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(true);
        EntityRegainHealthEvent event = regain(player, EntityRegainHealthEvent.RegainReason.SATIATED);

        listener(w -> true).onRegainHealth(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("a non-SATIATED regain reason is left uncancelled even while active")
    void otherReasonLeftUncancelled() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(true);
        EntityRegainHealthEvent event = regain(player, EntityRegainHealthEvent.RegainReason.REGEN);

        listener(w -> true).onRegainHealth(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("an inactive challenge leaves SATIATED regen uncancelled")
    void inactiveLeavesSatiatedUncancelled() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(false);
        EntityRegainHealthEvent event = regain(player, EntityRegainHealthEvent.RegainReason.SATIATED);

        listener(w -> true).onRegainHealth(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a non-matching world leaves SATIATED regen uncancelled")
    void nonMatchingWorldLeavesSatiatedUncancelled() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(true);
        EntityRegainHealthEvent event = regain(player, EntityRegainHealthEvent.RegainReason.SATIATED);

        listener(w -> false).onRegainHealth(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a non-player entity regaining health is left untouched")
    void nonPlayerEntityLeftUntouched() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(true);
        Zombie zombie = world.spawn(new Location(world, 0, 64, 0), Zombie.class);
        EntityRegainHealthEvent event = regain(zombie, EntityRegainHealthEvent.RegainReason.SATIATED);

        listener(w -> true).onRegainHealth(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("challengeId returns the constant challenge id")
    void challengeIdReturnsConstant() {
        assertThat(listener(w -> true).challengeId()).isEqualTo(NoNaturalRegenerationListener.CHALLENGE_ID);
    }

    // ----- round completion on the active-to-inactive transition -----

    @Test
    @DisplayName("an active-to-inactive transition completes the round task for every online player in a "
            + "matching world, unconditionally")
    void activeToInactiveTransitionCompletesTaskForEveryMatchingPlayer() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(true);
        NoNaturalRegenerationListener listener = listener(w -> true);
        Runnable onCatalogChanged = enableAndCaptureCatalogListener(listener);

        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(false);
        onCatalogChanged.run();

        verify(taskProgress).completeTask(player.getUniqueId(), NoNaturalRegenerationListener.CHALLENGE_ID,
                "endured_the_round");
    }

    @Test
    @DisplayName("a player in a non-matching world is not swept into completion on the active-to-inactive transition")
    void nonMatchingWorldPlayerNotSweptOnTransition() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(true);
        NoNaturalRegenerationListener listener = listener(w -> false);
        Runnable onCatalogChanged = enableAndCaptureCatalogListener(listener);

        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(false);
        onCatalogChanged.run();

        verifyNoInteractions(taskProgress);
    }

    @Test
    @DisplayName("an inactive-to-active transition does not trigger a completion sweep")
    void inactiveToActiveTransitionDoesNotSweep() {
        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(false);
        NoNaturalRegenerationListener listener = listener(w -> true);
        Runnable onCatalogChanged = enableAndCaptureCatalogListener(listener);

        when(challenges.isActive(NoNaturalRegenerationListener.CHALLENGE_ID)).thenReturn(true);
        onCatalogChanged.run();

        verifyNoInteractions(taskProgress);
    }

    @Test
    @DisplayName("enable registers this listener with the plugin manager")
    void enableRegistersListener() {
        NoNaturalRegenerationListener behavior = listener(w -> true);

        behavior.enable();

        assertThat(isRegistered(EntityRegainHealthEvent.getHandlerList(), behavior)).isTrue();
    }

    @Test
    @DisplayName("disable unregisters this listener")
    void disableUnregistersListener() {
        NoNaturalRegenerationListener behavior = listener(w -> true);
        behavior.enable();

        behavior.disable();

        assertThat(isRegistered(EntityRegainHealthEvent.getHandlerList(), behavior)).isFalse();
    }
}
