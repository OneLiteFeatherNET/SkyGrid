package net.onelitefeather.skygridng.core.solver;

import net.onelitefeather.skygridng.api.model.AdjacencyRules;
import net.onelitefeather.skygridng.api.model.Direction;
import net.onelitefeather.skygridng.api.model.TileType;
import net.onelitefeather.skygridng.api.util.Seeds;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Wave Function Collapse core (simple tiled model) on a rectangular grid.
 *
 * A pure algorithm, free of framework and IO dependencies and therefore fully testable. Entropy
 * and collapse behavior are injected strategies (open for extension, closed for modification).
 *
 * Domains are long bitmasks (max 64 tile types). Propagation is pure bit logic and only processes
 * changed cells via a stack. Because width and height are free, the same solver handles 2D
 * regions and 1D border lines (height == 1).
 *
 * Determinism: only the given seed drives tie breaks and choices.
 * Contradiction: up to maxAttempts restarts with a salted seed, then a locally greedy fallback
 * fill guarantees a rule conformant result.
 */
public final class WfcSolver {

    /** Returns the initial domain mask of a local cell (for example fixed borders). */
    @FunctionalInterface
    public interface CellPreset {
        long maskAt(int localX, int localZ);
    }

    private final EntropyHeuristic heuristic;
    private final CollapseStrategy collapseStrategy;

    public WfcSolver(EntropyHeuristic heuristic, CollapseStrategy collapseStrategy) {
        this.heuristic = heuristic;
        this.collapseStrategy = collapseStrategy;
    }

    public int[] solve(
            int width,
            int height,
            long baseSeed,
            AdjacencyRules adjacency,
            List<TileType> tiles,
            long fullMask,
            int maxAttempts,
            int fallbackIndex,
            CellPreset preset
    ) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return attemptSolve(width, height, baseSeed + attempt * 0x9E3779B1L, adjacency, tiles, fullMask, preset);
            } catch (Contradiction ignored) {
                // next attempt with a salted seed
            }
        }
        return fillFallback(width, height, baseSeed, adjacency, fullMask, fallbackIndex, preset);
    }

    private static final class Contradiction extends RuntimeException {
        Contradiction() {
            super(null, null, false, false);
        }
    }

    private int[] attemptSolve(
            int width, int height, long seed, AdjacencyRules adjacency, List<TileType> tiles, long fullMask, CellPreset preset) {
        int cellCount = width * height;
        long[] domain = new long[cellCount];
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < cellCount; i++) {
            int lx = i % width;
            int lz = i / width;
            domain[i] = preset.maskAt(lx, lz) & fullMask;
            if (domain[i] != fullMask) {
                stack.push(i);
            }
        }
        RandomGenerator random = Seeds.rng(seed);
        propagate(domain, width, height, adjacency, stack);

        int next;
        while ((next = lowestEntropyCell(domain, tiles, random)) >= 0) {
            int chosen = collapseStrategy.collapse(domain[next], tiles, random);
            domain[next] = 1L << chosen;
            stack.push(next);
            propagate(domain, width, height, adjacency, stack);
        }

        int[] result = new int[cellCount];
        for (int i = 0; i < cellCount; i++) {
            result[i] = Long.numberOfTrailingZeros(domain[i]);
        }
        return result;
    }

    private int lowestEntropyCell(long[] domain, List<TileType> tiles, RandomGenerator random) {
        int best = -1;
        double bestEntropy = Double.MAX_VALUE;
        for (int i = 0; i < domain.length; i++) {
            if (Long.bitCount(domain[i]) <= 1) {
                continue;
            }
            double entropy = heuristic.entropy(domain[i], tiles) + random.nextDouble() * 1e-6;
            if (entropy < bestEntropy) {
                bestEntropy = entropy;
                best = i;
            }
        }
        return best;
    }

    private void propagate(long[] domain, int width, int height, AdjacencyRules adjacency, Deque<Integer> stack) {
        while (!stack.isEmpty()) {
            int current = stack.pop();
            int cx = current % width;
            int cz = current / width;
            long currentMask = domain[current];
            for (Direction dir : Direction.values()) {
                int nx = cx + dir.dx();
                int nz = cz + dir.dz();
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                    continue;
                }
                int neighbor = nz * width + nx;
                long support = adjacency.support(currentMask, dir);
                long before = domain[neighbor];
                long after = before & support;
                if (after == before) {
                    continue;
                }
                if (after == 0L) {
                    throw new Contradiction();
                }
                domain[neighbor] = after;
                stack.push(neighbor);
            }
        }
    }

    private int[] fillFallback(
            int width, int height, long baseSeed, AdjacencyRules adjacency, long fullMask, int fallbackIndex, CellPreset preset) {
        RandomGenerator random = Seeds.rng(baseSeed ^ 0x5DEECE66DL);
        int[] out = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int idx = z * width + x;
                long mask = preset.maskAt(x, z) & fullMask;
                if (z > 0) {
                    mask &= adjacency.support(1L << out[idx - width], Direction.SOUTH);
                }
                if (x > 0) {
                    mask &= adjacency.support(1L << out[idx - 1], Direction.EAST);
                }
                out[idx] = mask == 0L ? fallbackIndex : pickAny(mask, random);
            }
        }
        return out;
    }

    private int pickAny(long mask, RandomGenerator random) {
        int count = Long.bitCount(mask);
        int target = random.nextInt(count);
        long m = mask;
        for (int i = 0; i < target; i++) {
            m &= (m - 1);
        }
        return Long.numberOfTrailingZeros(m);
    }
}
