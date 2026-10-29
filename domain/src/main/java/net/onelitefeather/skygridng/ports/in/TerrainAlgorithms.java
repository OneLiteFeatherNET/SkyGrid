package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.ports.algorithm.TerrainAlgorithm;

import java.util.Set;

/**
 * In port: the catalog of terrain algorithms.
 *
 * Named after its purpose (the catalog), not its mechanism. It exposes the currently selected
 * algorithm (rebuilt when the configuration changes) and which algorithm ids are known, so the
 * generator and the admin never need to know how an algorithm is built or which ids exist.
 */
public interface TerrainAlgorithms {

    /** The currently selected algorithm. */
    TerrainAlgorithm current();

    /** True if {@code id} names a known algorithm. */
    boolean isKnown(String id);

    /** The ids of all known algorithms, in registration order. */
    Set<String> ids();
}
