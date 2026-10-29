package net.onelitefeather.skygridng.platforms.olf.season;

import net.onelitefeather.skygridng.api.model.SeasonRun;
import net.onelitefeather.skygridng.platforms.common.WorldGenerators;
import net.onelitefeather.skygridng.ports.out.SeasonWorlds;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Bukkit implementation of {@link SeasonWorlds}. Creating, unloading and teleporting run on the main thread and
 * only while the worlds are not ticking; the folder checks and the archive move are file operations on the calling
 * thread, which is the async season tick.
 *
 * <p>Season worlds live in {@code <level>/dimensions/skygridng/season_<id>}, the layout of Paper 26.1. The level folder
 * is supplied, so tests point it at a temporary directory.
 */
public final class BukkitSeasonWorlds implements SeasonWorlds {

    private static final String DIMENSIONS = "dimensions";

    private final Plugin plugin;
    private final WorldGenerators generators;
    private final Supplier<Path> levelFolder;
    private final Supplier<Path> archiveDirectory;

    public BukkitSeasonWorlds(Plugin plugin, WorldGenerators generators, Supplier<Path> levelFolder,
                              Supplier<Path> archiveDirectory) {
        this.plugin = plugin;
        this.generators = generators;
        this.levelFolder = levelFolder;
        this.archiveDirectory = archiveDirectory;
    }

    @Override
    public void prepare(String worldKey, long seed, String preset) {
        MainThread.call(plugin, () -> {
            World world = Bukkit.createWorld(WorldCreator.ofKey(key(worldKey))
                    .seed(seed)
                    .generator(generators.forWorld(worldKey, preset))
                    .environment(World.Environment.NORMAL)
                    .forcedSpawnPosition(Position.block(0, 100, 0), 0f, 0f));
            if (world == null) {
                throw new IllegalStateException("the server did not create the season world " + worldKey);
            }
            return null;
        });
    }

    @Override
    public boolean isLoaded(String worldKey) {
        return Bukkit.getWorld(key(worldKey)) != null;
    }

    @Override
    public boolean exists(String worldKey) {
        return Files.isDirectory(seasonFolder(worldKey));
    }

    @Override
    public boolean archived(String worldKey) {
        return Files.isDirectory(archiveDirectory.get().resolve(folderName(worldKey)));
    }

    @Override
    public List<UUID> evacuate(String fromKey, String toKey) {
        return MainThread.call(plugin, () -> {
            World from = Bukkit.getWorld(key(fromKey));
            World to = Bukkit.getWorld(key(toKey));
            List<UUID> moved = new ArrayList<>();
            if (from == null) {
                return moved;
            }
            if (to == null) {
                throw new IllegalStateException("the target season world " + toKey + " is not loaded");
            }
            for (Player player : List.copyOf(from.getPlayers())) {
                player.teleport(to.getSpawnLocation());
                moved.add(player.getUniqueId());
            }
            return moved;
        });
    }

    @Override
    public boolean unload(String worldKey) {
        return MainThread.call(plugin, () -> {
            World world = Bukkit.getWorld(key(worldKey));
            if (world == null) {
                return true;
            }
            if (!world.getPlayers().isEmpty() || Bukkit.isTickingWorlds()) {
                return false;
            }
            world.save();
            return Bukkit.unloadWorld(world, true);
        });
    }

    @Override
    public void archive(String worldKey, SeasonRun manifest) {
        Path source = seasonFolder(worldKey);
        Path directory = archiveDirectory.get();
        Path target = directory.resolve(folderName(worldKey));
        try {
            if (Files.exists(target)) {
                throw new IllegalStateException("the archive target already exists: " + target);
            }
            // The manifest goes into the folder first, so a failure cannot leave an archive without its manifest.
            Files.writeString(source.resolve("season.json"), manifestJson(manifest));
            Files.createDirectories(directory);
            Files.move(source, target);
        } catch (IOException e) {
            throw new UncheckedIOException("could not archive " + worldKey, e);
        }
    }

    private Path seasonFolder(String worldKey) {
        NamespacedKey key = key(worldKey);
        return levelFolder.get().resolve(DIMENSIONS).resolve(key.getNamespace()).resolve(key.getKey());
    }

    private static String folderName(String worldKey) {
        return key(worldKey).getKey();
    }

    private static NamespacedKey key(String worldKey) {
        return NamespacedKey.fromString(worldKey);
    }

    private static String manifestJson(SeasonRun run) {
        return "{\n"
                + "  \"seasonId\": \"" + run.seasonId().value() + "\",\n"
                + "  \"worldKey\": \"" + run.worldKey() + "\",\n"
                + "  \"seed\": " + run.seed() + ",\n"
                + "  \"startedAt\": " + quoted(run.startedAt()) + ",\n"
                + "  \"endedAt\": " + quoted(run.endedAt()) + "\n"
                + "}\n";
    }

    private static String quoted(Instant instant) {
        return instant == null ? "null" : "\"" + instant + "\"";
    }
}
