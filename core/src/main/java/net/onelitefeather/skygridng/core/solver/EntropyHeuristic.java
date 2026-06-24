package net.onelitefeather.skygridng.core.solver;

import net.onelitefeather.skygridng.api.model.TileType;

import java.util.List;

/**
 * Strategy: rates the uncertainty of a cell. The solver collapses the cell with the smallest
 * value first. Swappable without changing the solver (open for extension).
 */
@FunctionalInterface
public interface EntropyHeuristic {

    /**
     * @param domainMask bitmask of the tiles still possible for this cell
     * @param tiles      tile list (index = bit position), provides weights
     * @return uncertainty measure; smaller means collapse first
     */
    double entropy(long domainMask, List<TileType> tiles);
}
