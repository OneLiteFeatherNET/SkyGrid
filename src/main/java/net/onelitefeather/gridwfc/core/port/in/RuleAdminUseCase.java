package net.onelitefeather.gridwfc.core.port.in;

import net.onelitefeather.gridwfc.core.model.GeneratorConfig;
import net.onelitefeather.gridwfc.core.model.TileType;

import java.util.List;
import java.util.Optional;

/**
 * In port: in game administration of the configuration (called by the command adapter).
 *
 * All changes act on an immutable snapshot; undo uses the previous config as a Memento.
 * {@code save} writes through the {@code RuleRepository} out port.
 */
public interface RuleAdminUseCase {

    GeneratorConfig current();

    /** Active algorithm id ("wfc", "skygrid", "noise_skygrid"). */
    String algorithm();

    /** Switches the active algorithm. Returns false for an unknown id. */
    boolean setAlgorithm(String algorithmId);

    List<String> tileIds();

    Optional<TileType> tile(String id);

    /** Reloads the configuration from JSON and invalidates caches (undo history is cleared). */
    void reload();

    /** Persists the current state back to the JSON file. */
    void save();

    boolean setWeight(String tileId, double weight);

    boolean allow(String tileA, String tileB);

    boolean disallow(String tileA, String tileB);

    /** Sets a SkyGrid block weight (adds the material if absent). */
    boolean setSkyGridWeight(String material, double weight);

    /** Reverts the last change. */
    boolean undo();
}
