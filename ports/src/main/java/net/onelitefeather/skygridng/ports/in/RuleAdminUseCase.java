package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.GeneratorConfig;

/**
 * In port: in game administration of the configuration (called by the command adapter).
 *
 * All changes act on an immutable snapshot; undo uses the previous config as a Memento.
 * {@code save} flattens the edits back into the active preset and persists the catalog.
 */
public interface RuleAdminUseCase {

    GeneratorConfig current();

    /** Active algorithm id ("skygrid", "noise_skygrid"). */
    String algorithm();

    /** Switches the active algorithm. Returns false for an unknown id. */
    boolean setAlgorithm(String algorithmId);

    /** Reloads the preset catalog from disk, re-resolves the active preset, clears the undo history. */
    void reload();

    /** Flattens the current state into the active preset and persists the catalog. */
    void save();

    /** Sets a SkyGrid block weight (adds the material if absent). */
    boolean setSkyGridWeight(String material, double weight);

    /** Reverts the last change. */
    boolean undo();
}
