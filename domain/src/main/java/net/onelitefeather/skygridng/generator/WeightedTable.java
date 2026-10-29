package net.onelitefeather.skygridng.generator;


import java.util.List;

/**
 * A precomputed weighted lookup table over block ids.
 *
 * Built once from a block list, then sampled in O(log n) by a roll in [0,1). Used by the
 * SkyGrid algorithms, which pick a block per grid cell from a deterministic roll.
 */
public final class WeightedTable {

    private final BlockId[] materials;
    private final double[] cumulative; // normalized cumulative weights in (0,1]
    private final double total;

    private WeightedTable(BlockId[] materials, double[] cumulative, double total) {
        this.materials = materials;
        this.cumulative = cumulative;
        this.total = total;
    }

    public static WeightedTable of(List<BlockWeight> blocks) {
        int n = blocks.size();
        BlockId[] materials = new BlockId[n];
        double[] cumulative = new double[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += blocks.get(i).weight();
        }
        double running = 0.0;
        for (int i = 0; i < n; i++) {
            materials[i] = blocks.get(i).material();
            running += blocks.get(i).weight();
            cumulative[i] = running / sum;
        }
        return new WeightedTable(materials, cumulative, sum);
    }

    /**
     * Samples a block for a roll in [0,1). Deterministic for a given roll.
     */
    public BlockId sample(double roll) {
        double clamped = roll <= 0.0 ? 0.0 : Math.min(roll, 0.999999999);
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (clamped < cumulative[mid]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return materials[lo];
    }

    public double totalWeight() {
        return total;
    }

    public int size() {
        return materials.length;
    }
}
