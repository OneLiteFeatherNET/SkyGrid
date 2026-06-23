package net.onelitefeather.skygridng.bootstrap;

import net.onelitefeather.skygridng.adapter.in.paper.HexagonalChunkGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box integration test for the composition root. MockBukkit boots a server and loads the
 * plugin, which builds the full Guice graph, registers the command and writes the default config.
 * If any wiring is wrong the plugin fails to enable and these tests fail.
 */
class SkyGridNgPluginTest {

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
    @DisplayName("the plugin enables, registers /skygrid and writes generator.json")
    void enablesAndWires() {
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);

        assertThat(plugin.isEnabled()).isTrue();
        assertThat(plugin.getCommand("skygrid")).isNotNull();
        assertThat(new File(plugin.getDataFolder(), "generator.json")).exists();
    }

    @Test
    @DisplayName("the default world generator is the hexagonal chunk generator")
    void providesGenerator() {
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);

        ChunkGenerator generator = plugin.getDefaultWorldGenerator("world", null);

        assertThat(generator).isInstanceOf(HexagonalChunkGenerator.class);
    }

    @Test
    @DisplayName("disable runs cleanly")
    void disableIsClean() {
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);
        server.getPluginManager().disablePlugin(plugin);
        assertThat(plugin.isEnabled()).isFalse();
    }
}
