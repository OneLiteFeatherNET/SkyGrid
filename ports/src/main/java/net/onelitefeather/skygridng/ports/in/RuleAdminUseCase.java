package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.TileType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * In port: in game administration of the configuration (called by the command adapter).
 *
 * All changes act on an immutable snapshot; undo uses the previous config as a Memento.
 * {@code save} flattens the edits back into the active preset and persists the catalog.
 */
public interface RuleAdminUseCase {

    GeneratorConfig current();

    /** Active algorithm id ("wfc", "skygrid", "noise_skygrid"). */
    String algorithm();

    /** Switches the active algorithm. Returns false for an unknown id. */
    boolean setAlgorithm(String algorithmId);

    /** All registered algorithm ids, for validation and tab completion. */
    Set<String> algorithmIds();

    List<String> tileIds();

    Optional<TileType> tile(String id);

    /** Reloads the preset catalog from disk, re-resolves the active preset, clears the undo history. */
    void reload();

    /** Flattens the current state into the active preset and persists the catalog. */
    void save();

    boolean setWeight(String tileId, double weight);

    boolean allow(String tileA, String tileB);

    boolean disallow(String tileA, String tileB);

    /** Sets a SkyGrid block weight (adds the material if absent). */
    boolean setSkyGridWeight(String material, double weight);

    /** Reverts the last change. */
    boolean undo();
}
