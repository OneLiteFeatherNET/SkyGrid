package net.onelitefeather.skygridng.platforms.olf;

import net.onelitefeather.skygridng.adapters.challenge.NoCraftingTableListener;
import net.onelitefeather.skygridng.adapters.challenge.NoNaturalRegenerationListener;
import net.onelitefeather.skygridng.adapters.challenge.OneLifeListener;
import net.onelitefeather.skygridng.api.SkyGridApi;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the OLF distribution. MockBukkit boots a server and loads the plugin, which
 * builds the base Guice graph plus the OLF feature module and enables every bound feature. If the
 * feature wiring is wrong (a missing binding or an injection error), enabling throws and the plugin
 * does not enable, so a green enable verifies the whole seam.
 */
class OlfNetworkPluginTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the OLF distribution enables the base generator, the API, and its features")
    void enables() {
        OlfNetworkPlugin plugin = MockBukkit.load(OlfNetworkPlugin.class);

        assertThat(plugin.isEnabled()).isTrue();
        assertThat(plugin.getCommand("skygrid")).isNotNull();
        assertThat(server.getServicesManager().load(SkyGridApi.class)).isNotNull();
    }

    @Test
    @DisplayName("disable runs cleanly")
    void disableIsClean() {
        OlfNetworkPlugin plugin = MockBukkit.load(OlfNetworkPlugin.class);

        server.getPluginManager().disablePlugin(plugin);

        assertThat(plugin.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("ChallengeFeature registers the NoCraftingTableListener with the plugin manager on enable")
    void challengeFeatureRegistersItsListener() {
        MockBukkit.load(OlfNetworkPlugin.class);

        boolean registered = false;
        for (RegisteredListener registration : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
            if (registration.getListener() instanceof NoCraftingTableListener) {
                registered = true;
            }
        }

        assertThat(registered).isTrue();
    }

    @Test
    @DisplayName("disabling the plugin unregisters the NoCraftingTableListener")
    void disableUnregistersChallengeListener() {
        OlfNetworkPlugin plugin = MockBukkit.load(OlfNetworkPlugin.class);

        server.getPluginManager().disablePlugin(plugin);

        for (RegisteredListener registration : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
            assertThat(registration.getListener()).isNotInstanceOf(NoCraftingTableListener.class);
        }
    }

    @Test
    @DisplayName("ChallengeFeature registers the OneLifeListener with the plugin manager on enable")
    void challengeFeatureRegistersOneLifeListener() {
        MockBukkit.load(OlfNetworkPlugin.class);

        assertThat(hasListenerOfType(PlayerDeathEvent.getHandlerList(), OneLifeListener.class)).isTrue();
        assertThat(hasListenerOfType(PlayerRespawnEvent.getHandlerList(), OneLifeListener.class)).isTrue();
        assertThat(hasListenerOfType(PlayerJoinEvent.getHandlerList(), OneLifeListener.class)).isTrue();
    }

    @Test
    @DisplayName("ChallengeFeature registers the NoNaturalRegenerationListener with the plugin manager on enable")
    void challengeFeatureRegistersNoNaturalRegenerationListener() {
        MockBukkit.load(OlfNetworkPlugin.class);

        assertThat(hasListenerOfType(EntityRegainHealthEvent.getHandlerList(), NoNaturalRegenerationListener.class))
                .isTrue();
    }

    @Test
    @DisplayName("disabling the plugin unregisters the OneLifeListener and the NoNaturalRegenerationListener")
    void disableUnregistersOneLifeAndNoNaturalRegenerationListeners() {
        OlfNetworkPlugin plugin = MockBukkit.load(OlfNetworkPlugin.class);

        server.getPluginManager().disablePlugin(plugin);

        assertThat(hasListenerOfType(PlayerDeathEvent.getHandlerList(), OneLifeListener.class)).isFalse();
        assertThat(hasListenerOfType(PlayerRespawnEvent.getHandlerList(), OneLifeListener.class)).isFalse();
        assertThat(hasListenerOfType(PlayerJoinEvent.getHandlerList(), OneLifeListener.class)).isFalse();
        assertThat(hasListenerOfType(EntityRegainHealthEvent.getHandlerList(), NoNaturalRegenerationListener.class))
                .isFalse();
        // The LevelBorderWarningTask is a plain Runnable scheduled via runTaskTimer, not a Listener,
        // so its cancellation is not observable through a HandlerList the way the listeners above
        // are. MockBukkit's scheduler does not expose per-task cancellation state either, so the
        // best honest assertion here is that disabling the plugin completed without throwing.
        assertThat(plugin.isEnabled()).isFalse();
    }

    private static boolean hasListenerOfType(org.bukkit.event.HandlerList handlerList, Class<?> type) {
        for (RegisteredListener registration : handlerList.getRegisteredListeners()) {
            if (type.isInstance(registration.getListener())) {
                return true;
            }
        }
        return false;
    }
}
