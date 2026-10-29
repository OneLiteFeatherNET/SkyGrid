package net.onelitefeather.skygridng.platforms.common;

import net.onelitefeather.skygridng.generator.SkyGridApi;
import net.onelitefeather.skygridng.generator.HexagonalChunkGenerator;
import net.onelitefeather.skygridng.challenge.NoCraftingTableListener;
import net.onelitefeather.skygridng.challenge.NoNaturalRegenerationListener;
import net.onelitefeather.skygridng.challenge.OneLifeListener;
import net.onelitefeather.skygridng.season.SeasonId;
import net.onelitefeather.skygridng.season.CurrentSeason;
import net.onelitefeather.skygridng.season.SeasonAdminUseCase;
import net.onelitefeather.skygridng.persistence.Database;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box integration test for the composition root. MockBukkit boots a server and loads the
 * plugin, which builds the full Guice graph, writes the default config, and (since
 * {@link SkyGridNgPlugin} now folds in what used to be {@code OlfNetworkPlugin}) enables the
 * OLF-only challenge features as well. If any wiring is wrong the plugin fails to enable and these
 * tests fail.
 *
 * Command registration itself is deliberately not asserted here: since the stage 2 Cloud command
 * migration, {@code /skygrid} is registered by {@code CloudCommandRegistrar} through a
 * {@code PaperCommandManager}, whose constructor reflects into the server's real Brigadier argument
 * type registry. MockBukkit does not provide that registry, so the constructor throws a
 * {@code LinkageError} there, which {@link SkyGridNgPlugin#onEnable} deliberately catches so the rest
 * of the plugin still enables (see its Javadoc); there is no longer a plugin.yml-declared
 * {@code PluginCommand} to observe through {@code getCommand} either. Real command registration is
 * exercised by a boot test against an actual Paper server instead.
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
    @DisplayName("the plugin enables and writes config.yml")
    void enablesAndWires() {
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);

        assertThat(plugin.isEnabled()).isTrue();
        assertThat(new File(plugin.getDataFolder(), "config.yml")).exists();
        assertThat(new File(plugin.getDataFolder(), "presets/classic.yml")).exists();
        assertThat(server.getServicesManager().load(SkyGridApi.class)).isNotNull();
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

    @Test
    @DisplayName("ChallengeFeature registers the NoCraftingTableListener with the plugin manager on enable")
    void challengeFeatureRegistersItsListener() {
        MockBukkit.load(SkyGridNgPlugin.class);

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
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);

        server.getPluginManager().disablePlugin(plugin);

        for (RegisteredListener registration : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
            assertThat(registration.getListener()).isNotInstanceOf(NoCraftingTableListener.class);
        }
    }

    @Test
    @DisplayName("ChallengeFeature registers the OneLifeListener with the plugin manager on enable")
    void challengeFeatureRegistersOneLifeListener() {
        MockBukkit.load(SkyGridNgPlugin.class);

        assertThat(hasListenerOfType(PlayerDeathEvent.getHandlerList(), OneLifeListener.class)).isTrue();
        assertThat(hasListenerOfType(PlayerRespawnEvent.getHandlerList(), OneLifeListener.class)).isTrue();
        assertThat(hasListenerOfType(PlayerJoinEvent.getHandlerList(), OneLifeListener.class)).isTrue();
    }

    @Test
    @DisplayName("ChallengeFeature registers the NoNaturalRegenerationListener with the plugin manager on enable")
    void challengeFeatureRegistersNoNaturalRegenerationListener() {
        MockBukkit.load(SkyGridNgPlugin.class);

        assertThat(hasListenerOfType(EntityRegainHealthEvent.getHandlerList(), NoNaturalRegenerationListener.class))
                .isTrue();
    }

    @Test
    @DisplayName("disabling the plugin unregisters the OneLifeListener and the NoNaturalRegenerationListener")
    void disableUnregistersOneLifeAndNoNaturalRegenerationListeners() {
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);

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

    private static boolean hasListenerOfType(HandlerList handlerList, Class<?> type) {
        for (RegisteredListener registration : handlerList.getRegisteredListeners()) {
            if (type.isInstance(registration.getListener())) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("the one database is a singleton, so disable closes the single pool every repository uses")
    void theDatabaseIsOneSingletonSharedByEveryRepository() {
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);

        assertThat(plugin.injector().getInstance(Database.class))
                .as("every request for the database must yield the same instance, so disable closes that one pool")
                .isSameAs(plugin.injector().getInstance(Database.class));
    }

    @Test
    @DisplayName("an empty seasons directory keeps the legacy season and schedules no transition")
    void emptySeasonsDirectoryKeepsTheLegacySeason() {
        SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);
        var seasons = plugin.injector().getInstance(SeasonAdminUseCase.class);

        assertThat(plugin.injector().getInstance(CurrentSeason.class).id())
                .as("without configured seasons every progress write goes to the legacy season")
                .isEqualTo(SeasonId.LEGACY);
        assertThat(seasons.list().seasons())
                .as("no season is configured, so none is loaded")
                .isEmpty();
        assertThat(seasons.tick(Instant.parse("2027-01-01T00:00:00Z")))
                .as("no transition is scheduled, so a tick does nothing")
                .isFalse();
        assertThat(Bukkit.getWorlds())
                .as("no season world is created")
                .noneMatch(world -> world.getKey().getKey().startsWith("season_"));
    }
}
