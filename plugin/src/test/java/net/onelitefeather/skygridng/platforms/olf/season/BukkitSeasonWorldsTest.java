package net.onelitefeather.skygridng.platforms.olf.season;

import net.onelitefeather.skygridng.api.model.SeasonId;
import net.onelitefeather.skygridng.api.model.SeasonPhase;
import net.onelitefeather.skygridng.api.model.SeasonRun;
import net.onelitefeather.skygridng.generator.WorldGenerators;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Black-box tests for the Bukkit season world adapter on MockBukkit's server. The season folder and the archive
 * live in a {@link TempDir}, so no test touches the real server directory. The world creation itself is only
 * exercised through MockBukkit's {@code createWorld}; the generator is a Mockito double.
 */
class BukkitSeasonWorldsTest {

    private static final String ACTIVE = "skygridng:season_s01";
    private static final String NEXT = "skygridng:season_s02";

    private ServerMock server;
    private Plugin plugin;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SkyGridNg");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Path level() {
        return tempDir.resolve("world");
    }

    private Path archive() {
        return tempDir.resolve("archive");
    }

    private BukkitSeasonWorlds worlds() {
        return new BukkitSeasonWorlds(plugin, mock(WorldGenerators.class), this::level, this::archive);
    }

    /** The folder a season world is stored in, as the adapter resolves it: dimensions/namespace/key. */
    private Path seasonFolder(String key) throws IOException {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        Path folder = level().resolve("dimensions").resolve(parsed.getNamespace()).resolve(parsed.getKey());
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("level.dat"), "data");
        return folder;
    }

    private World loadWorld(String key) {
        return server.createWorld(WorldCreator.ofKey(NamespacedKey.fromString(key)));
    }

    private static SeasonRun archivingRun() {
        return new SeasonRun(new SeasonId("s01"), ACTIVE, 7L, SeasonPhase.ARCHIVING, 0, null, null);
    }

    @Test
    void evacuationMovesEveryPlayerIntoTheTargetWorld() {
        World source = loadWorld(ACTIVE);
        World target = loadWorld(NEXT);
        PlayerMock player = server.addPlayer();
        player.teleport(source.getSpawnLocation());

        List<java.util.UUID> moved = worlds().evacuate(ACTIVE, NEXT);

        assertThat(moved).as("the evacuation reports the player it moved").containsExactly(player.getUniqueId());
        assertThat(player.getWorld()).as("the player must end up in the target season world").isEqualTo(target);
    }

    @Test
    void unloadRefusesWhileAPlayerIsStillInside() {
        World season = loadWorld(ACTIVE);
        PlayerMock player = server.addPlayer();
        player.teleport(season.getSpawnLocation());

        boolean unloaded = worlds().unload(ACTIVE);

        assertThat(unloaded).as("a world with a player inside must not unload yet").isFalse();
        assertThat(worlds().isLoaded(ACTIVE)).as("the refused world stays loaded").isTrue();
    }

    @Test
    void archiveMovesTheFolderIntoTheArchiveAndKeepsItsFiles() throws IOException {
        seasonFolder(ACTIVE);

        worlds().archive(ACTIVE, archivingRun());

        assertThat(Files.isDirectory(archive().resolve("season_s01")))
                .as("the folder moves into the archive directory")
                .isTrue();
        assertThat(Files.exists(archive().resolve("season_s01").resolve("level.dat")))
                .as("the world files move with the folder")
                .isTrue();
        assertThat(Files.exists(level().resolve("dimensions/skygridng/season_s01")))
                .as("the season folder no longer exists in the server")
                .isFalse();
    }

    @Test
    void anArchiveWhoseManifestCannotBeWrittenIsNeverMoved() throws IOException {
        Path folder = seasonFolder(ACTIVE);
        Files.createDirectories(folder.resolve("season.json"));

        assertThatThrownBy(() -> worlds().archive(ACTIVE, archivingRun()))
                .as("a manifest that cannot be written must fail the archive")
                .isInstanceOf(RuntimeException.class);
        assertThat(Files.exists(archive().resolve("season_s01")))
                .as("the manifest is written before the move, so an archive without a manifest never exists")
                .isFalse();
        assertThat(Files.exists(folder.resolve("level.dat")))
                .as("the world stays in place when its manifest fails")
                .isTrue();
    }

    @Test
    void failedArchiveLeavesTheFolderInPlace() throws IOException {
        Path folder = seasonFolder(ACTIVE);
        Files.createDirectories(archive().resolve("season_s01"));

        assertThatThrownBy(() -> worlds().archive(ACTIVE, archivingRun()))
                .as("an archive whose target already exists must fail")
                .isInstanceOf(RuntimeException.class);
        assertThat(Files.exists(folder.resolve("level.dat")))
                .as("a failed archive must leave the world files untouched")
                .isTrue();
    }
}
