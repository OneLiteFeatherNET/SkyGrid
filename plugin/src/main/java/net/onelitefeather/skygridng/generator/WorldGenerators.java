package net.onelitefeather.skygridng.generator;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Picks the chunk generator for each world from the generator spec id.
 *
 * Paper passes the part after the colon in {@code SkyGrid-ng:<preset>} as the {@code id}. A blank id
 * (just {@code SkyGrid-ng}) means the default preset (the active one), and that world follows the
 * default generator. A named preset gets its own generator, cached per preset so worlds sharing a
 * preset share one instance and an edit to the preset reaches them all. An unknown id is logged and
 * falls back to the default generator, never {@code null} (which would silently hand the world to the
 * vanilla generator).
 */
@Singleton
public final class WorldGenerators {

    private final HexagonalChunkGenerator defaultGenerator;
    private final WorldGeneratorFactory factory;
    private final PresetService presets;
    private final Logger logger;
    private final Map<String, ChunkGenerator> byPreset = new ConcurrentHashMap<>();

    @Inject
    public WorldGenerators(HexagonalChunkGenerator defaultGenerator, WorldGeneratorFactory factory,
                           PresetService presets, JavaPlugin plugin) {
        this.defaultGenerator = defaultGenerator;
        this.factory = factory;
        this.presets = presets;
        this.logger = plugin.getLogger();
    }

    /** The generator for {@code worldName}, selected by the spec {@code id} (may be null/blank). */
    public ChunkGenerator forWorld(String worldName, String id) {
        String requested = id == null ? "" : id.trim();
        if (requested.isEmpty()) {
            presets.recordWorld(worldName, presets.active());
            return defaultGenerator;
        }
        String preset = match(requested);
        if (preset == null) {
            logger.warning("World '" + worldName + "' requested unknown preset '" + requested
                    + "'. Known presets: " + presets.names() + ". Falling back to the default preset '"
                    + presets.active() + "'.");
            presets.recordWorld(worldName, presets.active());
            return defaultGenerator;
        }
        presets.recordWorld(worldName, preset);
        return byPreset.computeIfAbsent(preset, p -> factory.build(presets.stateFor(p)));
    }

    /** The catalog preset name matching the requested id case-insensitively, or null if none does. */
    private String match(String requested) {
        for (String name : presets.names()) {
            if (name.equalsIgnoreCase(requested)) {
                return name;
            }
        }
        return null;
    }
}
