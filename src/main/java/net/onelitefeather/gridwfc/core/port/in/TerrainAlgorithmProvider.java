package net.onelitefeather.gridwfc.core.port.in;

import net.onelitefeather.gridwfc.core.algorithm.TerrainAlgorithm;

/**
 * In port: gives the Paper generator the currently selected algorithm.
 *
 * The provider rebuilds the algorithm when the configuration changes, so the generator always
 * sees the active one without knowing how it is built.
 */
public interface TerrainAlgorithmProvider {

    TerrainAlgorithm current();
}
