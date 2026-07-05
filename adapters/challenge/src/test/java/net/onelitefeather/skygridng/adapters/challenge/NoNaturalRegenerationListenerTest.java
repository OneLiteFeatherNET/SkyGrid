package net.onelitefeather.skygridng.adapters.challenge;

import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityRegainHealthEvent;
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
 * MockBukkit + Mockito tests for the driving adapter: MockBukkit supplies the player, world and a
 * non-player entity, Mockito mocks the {@link ChallengeAdminUseCase} port. The listener method is
 * invoked directly with a hand-built {@link EntityRegainHealthEvent}.
 */
class NoNaturalRegenerationListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private ChallengeAdminUseCase challenges;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("skygrid-world");
        player = server.addPlayer();
        challenges = mock(ChallengeAdminUseCase.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NoNaturalRegenerationListener listener(Predicate<World> appliesTo) {
        return new NoNaturalRegenerationListener(challenges, appliesTo);
    }

    private EntityRegainHealthEvent regain(Entity entity, EntityRegainHealthEvent.RegainReason reason) {
        return new EntityRegainHealthEvent(entity, 1.0, reason);
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
}
