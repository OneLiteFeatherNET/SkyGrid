package net.onelitefeather.skygridng.platforms.olf;

import net.onelitefeather.skygridng.adapters.challenge.NoCraftingTableListener;
import net.onelitefeather.skygridng.api.SkyGridApi;
import org.bukkit.event.player.PlayerInteractEvent;
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
}
