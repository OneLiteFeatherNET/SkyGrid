package net.onelitefeather.skygridng.core.solver;

import net.onelitefeather.skygridng.api.model.TileType;

import java.util.List;
import java.util.random.RandomGenerator;

/** Weighted random pick from the domain using the tile weights. */
public final class WeightedRandomCollapse implements CollapseStrategy {

    @Override
    public int collapse(long domainMask, List<TileType> tiles, RandomGenerator random) {
        double total = 0.0;
        long m = domainMask;
        while (m != 0L) {
            total += tiles.get(Long.numberOfTrailingZeros(m)).weight();
            m &= (m - 1);
        }
        double roll = random.nextDouble() * total;
        m = domainMask;
        int last = Long.numberOfTrailingZeros(domainMask);
        while (m != 0L) {
            int index = Long.numberOfTrailingZeros(m);
            roll -= tiles.get(index).weight();
            if (roll <= 0.0) {
                return index;
            }
            last = index;
            m &= (m - 1);
        }
        return last;
    }
}
