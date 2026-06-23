package net.onelitefeather.skygridng.core.solver;

import net.onelitefeather.skygridng.core.model.TileType;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Strategy: picks the final tile from a cell domain. Swappable (weighted, uniform, biased, ...)
 * without changing the solver.
 */
@FunctionalInterface
public interface CollapseStrategy {

    /** @return tile index the cell collapses to */
    int collapse(long domainMask, List<TileType> tiles, RandomGenerator random);
}
