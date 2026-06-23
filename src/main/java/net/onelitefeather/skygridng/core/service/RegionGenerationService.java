package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.core.model.AdjacencyRules;
import net.onelitefeather.skygridng.core.model.GenerationSettings;
import net.onelitefeather.skygridng.core.model.RuleSet;
import net.onelitefeather.skygridng.core.model.TileType;
import net.onelitefeather.skygridng.core.port.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.core.util.Seeds;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the WFC generation use case: solves regions per seed, caches them thread safely and
 * returns the tile type per global cell.
 *
 * Seamlessness via hierarchical WFC:
 *  1. Region grid lines are solved first as 1D WFC along the line. Both endpoints of a segment
 *     are fixed to an anchor tile; the interior is solved between them under the rules. Adjacent
 *     regions reference the exact same segment (same cache key), so they get identical border
 *     values.
 *  2. The 2D region then solves its interior against those fixed borders.
 * Every neighborship is therefore rule conformant: two cells are either in the same region (the
 * solver enforces adjacency) or separated by a fixed, internally valid border line.
 *
 * Observes rule changes: rebuild the snapshot, clear all caches.
 */
public final class RegionGenerationService implements GenerateRegionUseCase {

    private record VKey(long seed, int borderX, int regionZ) {
    }

    private record HKey(long seed, int borderZ, int regionX) {
    }

    private record RegionKey(long seed, int regionX, int regionZ) {
    }

    private record Snapshot(
            RuleSet rules, AdjacencyRules adjacency, long fullMask, int anchorIndex, long anchorMask, int regionSize) {
    }

    private static final long V_SALT = 0x5642_4F52_4452_0001L;
    private static final long H_SALT = 0x4842_4F52_4452_0002L;

    private final RuleState state;
    private final WfcSolver solver;
    private final ConcurrentHashMap<VKey, int[]> vBorders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<HKey, int[]> hBorders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, int[]> interiors = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot;

    public RegionGenerationService(RuleState state, WfcSolver solver) {
        this.state = state;
        this.solver = solver;
        rebuild();
        state.addChangeListener(this::rebuild);
    }

    private void rebuild() {
        RuleSet rules = state.current().wfc();
        int anchor = rules.tileIndex(rules.settings().fallbackTileId());
        this.snapshot = new Snapshot(
                rules,
                rules.buildAdjacency(),
                rules.fullMask(),
                anchor,
                1L << anchor,
                rules.settings().regionSize());
        vBorders.clear();
        hBorders.clear();
        interiors.clear();
    }

    @Override
    public RuleSet rules() {
        return snapshot.rules();
    }

    @Override
    public TileType tileAt(long worldSeed, int cellX, int cellZ) {
        Snapshot snap = this.snapshot;
        int rsz = snap.regionSize();
        boolean vBorder = Math.floorMod(cellX, rsz) == 0;
        boolean hBorder = Math.floorMod(cellZ, rsz) == 0;

        if (vBorder && hBorder) {
            return snap.rules().tiles().get(snap.anchorIndex());
        }
        if (vBorder) {
            int rz = Math.floorDiv(cellZ, rsz);
            int localZ = cellZ - rz * rsz;
            return snap.rules().tiles().get(verticalSegment(snap, worldSeed, cellX, rz)[localZ]);
        }
        if (hBorder) {
            int rx = Math.floorDiv(cellX, rsz);
            int localX = cellX - rx * rsz;
            return snap.rules().tiles().get(horizontalSegment(snap, worldSeed, cellZ, rx)[localX]);
        }
        int rx = Math.floorDiv(cellX, rsz);
        int rz = Math.floorDiv(cellZ, rsz);
        int localX = cellX - rx * rsz;
        int localZ = cellZ - rz * rsz;
        int[] grid = interior(snap, worldSeed, rx, rz);
        return snap.rules().tiles().get(grid[localZ * (rsz + 1) + localX]);
    }

    /** 1D solution of a vertical border line (endpoints = anchor). */
    private int[] verticalSegment(Snapshot snap, long seed, int borderX, int regionZ) {
        return vBorders.computeIfAbsent(new VKey(seed, borderX, regionZ), key -> {
            int rsz = snap.regionSize();
            GenerationSettings settings = snap.rules().settings();
            WfcSolver.CellPreset preset = (lx, lz) ->
                    (lz == 0 || lz == rsz) ? snap.anchorMask() : snap.fullMask();
            return solver.solve(1, rsz + 1,
                    Seeds.mix(seed ^ V_SALT, borderX, regionZ),
                    snap.adjacency(), snap.rules().tiles(), snap.fullMask(),
                    settings.maxSolverAttempts(), snap.anchorIndex(), preset);
        });
    }

    /** 1D solution of a horizontal border line (endpoints = anchor). */
    private int[] horizontalSegment(Snapshot snap, long seed, int borderZ, int regionX) {
        return hBorders.computeIfAbsent(new HKey(seed, borderZ, regionX), key -> {
            int rsz = snap.regionSize();
            GenerationSettings settings = snap.rules().settings();
            WfcSolver.CellPreset preset = (lx, lz) ->
                    (lx == 0 || lx == rsz) ? snap.anchorMask() : snap.fullMask();
            return solver.solve(rsz + 1, 1,
                    Seeds.mix(seed ^ H_SALT, borderZ, regionX),
                    snap.adjacency(), snap.rules().tiles(), snap.fullMask(),
                    settings.maxSolverAttempts(), snap.anchorIndex(), preset);
        });
    }

    /** 2D solution of the region interior against the four fixed border lines. */
    private int[] interior(Snapshot snap, long seed, int rx, int rz) {
        return interiors.computeIfAbsent(new RegionKey(seed, rx, rz), key -> {
            int rsz = snap.regionSize();
            GenerationSettings settings = snap.rules().settings();
            int[] left = verticalSegment(snap, seed, rx * rsz, rz);
            int[] right = verticalSegment(snap, seed, (rx + 1) * rsz, rz);
            int[] top = horizontalSegment(snap, seed, rz * rsz, rx);
            int[] bottom = horizontalSegment(snap, seed, (rz + 1) * rsz, rx);

            WfcSolver.CellPreset preset = (lx, lz) -> {
                boolean l = lx == 0, r = lx == rsz, t = lz == 0, b = lz == rsz;
                if ((l || r) && (t || b)) {
                    return snap.anchorMask();
                }
                if (l) {
                    return 1L << left[lz];
                }
                if (r) {
                    return 1L << right[lz];
                }
                if (t) {
                    return 1L << top[lx];
                }
                if (b) {
                    return 1L << bottom[lx];
                }
                return snap.fullMask();
            };
            return solver.solve(rsz + 1, rsz + 1,
                    Seeds.regionSeed(seed, rx, rz),
                    snap.adjacency(), snap.rules().tiles(), snap.fullMask(),
                    settings.maxSolverAttempts(), snap.anchorIndex(), preset);
        });
    }
}
