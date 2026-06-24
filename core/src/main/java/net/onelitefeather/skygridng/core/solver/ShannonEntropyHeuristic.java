package net.onelitefeather.skygridng.core.solver;

import net.onelitefeather.skygridng.api.model.TileType;

import java.util.List;

/** Default heuristic: weighted Shannon entropy over the possible tiles. */
public final class ShannonEntropyHeuristic implements EntropyHeuristic {

    @Override
    public double entropy(long domainMask, List<TileType> tiles) {
        double sumW = 0.0;
        double sumWLogW = 0.0;
        long m = domainMask;
        while (m != 0L) {
            int index = Long.numberOfTrailingZeros(m);
            double w = tiles.get(index).weight();
            sumW += w;
            sumWLogW += w * Math.log(w);
            m &= (m - 1);
        }
        return Math.log(sumW) - sumWLogW / sumW;
    }
}
