package net.onelitefeather.skygridng.platforms.olf.challenge;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MockBukkit + Mockito tests for the driving adapter: MockBukkit supplies the player and world,
 * Mockito mocks the {@link ChallengeAdminUseCase} port. Each event handler is invoked directly on a
 * hand-built event, and the same listener instance is reused across a death/respawn (or death/join)
 * pair to observe the in-memory elimination set take effect, since it is not otherwise exposed.
 */
class OneLifeListenerTest {

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

    private OneLifeListener listener(Predicate<World> appliesTo) {
        return new OneLifeListener(challenges, appliesTo, plugin);
    }

    private static boolean isRegistered(HandlerList handlerList, Object listenerInstance) {
        for (RegisteredListener registration : handlerList.getRegisteredListeners()) {
            if (registration.getListener() == listenerInstance) {
                return true;
            }
        }
        return false;
    }

    private PlayerDeathEvent death(Player p) {
        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        return new PlayerDeathEvent(p, source, List.of(), 0, Component.empty(), false);
    }

    private PlayerRespawnEvent respawn(Player p) {
        return new PlayerRespawnEvent(p, new Location(world, 0, 64, 0), false, false, false,
                PlayerRespawnEvent.RespawnReason.DEATH);
    }

    private PlayerJoinEvent join(Player p) {
        return new PlayerJoinEvent(p, Component.empty());
    }

    @Test
    @DisplayName("death in a matching world while active eliminates the player, respawn forces spectator")
    void deathInMatchingWorldWhileActiveThenRespawnForcesSpectator() {
        when(challenges.isActive(OneLifeListener.CHALLENGE_ID)).thenReturn(true);
        OneLifeListener listener = listener(w -> true);

        listener.onDeath(death(player));
        listener.onRespawn(respawn(player));

        assertThat(player.getGameMode()).isEqualTo(GameMode.SPECTATOR);
    }

    @Test
    @DisplayName("death while the challenge is inactive does not eliminate the player")
    void deathWhileInactiveDoesNotEliminate() {
        when(challenges.isActive(OneLifeListener.CHALLENGE_ID)).thenReturn(false);
        GameMode before = player.getGameMode();
        OneLifeListener listener = listener(w -> true);

        listener.onDeath(death(player));
        listener.onRespawn(respawn(player));

        assertThat(player.getGameMode()).isEqualTo(before);
    }

    @Test
    @DisplayName("death in a non-matching world does not eliminate the player")
    void deathInNonMatchingWorldDoesNotEliminate() {
        when(challenges.isActive(OneLifeListener.CHALLENGE_ID)).thenReturn(true);
        GameMode before = player.getGameMode();
        OneLifeListener listener = listener(w -> false);

        listener.onDeath(death(player));
        listener.onRespawn(respawn(player));

        assertThat(player.getGameMode()).isEqualTo(before);
    }

    @Test
    @DisplayName("a join event for an eliminated player forces spectator too")
    void joinForEliminatedPlayerForcesSpectator() {
        when(challenges.isActive(OneLifeListener.CHALLENGE_ID)).thenReturn(true);
        OneLifeListener listener = listener(w -> true);

        listener.onDeath(death(player));
        listener.onJoin(join(player));

        assertThat(player.getGameMode()).isEqualTo(GameMode.SPECTATOR);
    }

    @Test
    @DisplayName("a join or respawn for a player who was never eliminated leaves their gamemode untouched")
    void neverEliminatedPlayerLeftUntouched() {
        when(challenges.isActive(OneLifeListener.CHALLENGE_ID)).thenReturn(true);
        GameMode before = player.getGameMode();
        OneLifeListener listener = listener(w -> true);

        listener.onJoin(join(player));
        listener.onRespawn(respawn(player));

        assertThat(player.getGameMode()).isEqualTo(before);
    }

    @Test
    @DisplayName("challengeId returns the constant challenge id")
    void challengeIdReturnsConstant() {
        assertThat(listener(w -> true).challengeId()).isEqualTo(OneLifeListener.CHALLENGE_ID);
    }

    @Test
    @DisplayName("enable registers this listener with the plugin manager for every handled event")
    void enableRegistersListener() {
        OneLifeListener behavior = listener(w -> true);

        behavior.enable();

        assertThat(isRegistered(PlayerDeathEvent.getHandlerList(), behavior)).isTrue();
        assertThat(isRegistered(PlayerRespawnEvent.getHandlerList(), behavior)).isTrue();
        assertThat(isRegistered(PlayerJoinEvent.getHandlerList(), behavior)).isTrue();
    }

    @Test
    @DisplayName("disable unregisters this listener from every handled event")
    void disableUnregistersListener() {
        OneLifeListener behavior = listener(w -> true);
        behavior.enable();

        behavior.disable();

        assertThat(isRegistered(PlayerDeathEvent.getHandlerList(), behavior)).isFalse();
        assertThat(isRegistered(PlayerRespawnEvent.getHandlerList(), behavior)).isFalse();
        assertThat(isRegistered(PlayerJoinEvent.getHandlerList(), behavior)).isFalse();
    }
}
