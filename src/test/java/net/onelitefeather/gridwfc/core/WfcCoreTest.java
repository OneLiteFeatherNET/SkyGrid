package net.onelitefeather.gridwfc.core;

import net.onelitefeather.gridwfc.core.algorithm.IndependentSkyGridAlgorithm;
import net.onelitefeather.gridwfc.core.algorithm.NoiseSkyGridAlgorithm;
import net.onelitefeather.gridwfc.core.algorithm.TerrainAlgorithm;
import net.onelitefeather.gridwfc.core.model.AllowedPair;
import net.onelitefeather.gridwfc.core.model.BlockWeight;
import net.onelitefeather.gridwfc.core.model.GenerationSettings;
import net.onelitefeather.gridwfc.core.model.GeneratorConfig;
import net.onelitefeather.gridwfc.core.model.RuleSet;
import net.onelitefeather.gridwfc.core.model.SkyGridConfig;
import net.onelitefeather.gridwfc.core.model.TileType;
import net.onelitefeather.gridwfc.core.service.AlgorithmService;
import net.onelitefeather.gridwfc.core.service.RegionGenerationService;
import net.onelitefeather.gridwfc.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.gridwfc.core.solver.WeightedRandomCollapse;
import net.onelitefeather.gridwfc.core.solver.WfcSolver;
import net.onelitefeather.gridwfc.core.state.RuleState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the hexagonal payoff: the core is fully testable without a server. Covers the retained
 * WFC algorithm and both new SkyGrid algorithms.
 */
class WfcCoreTest {

    private static final long SEED = 987654321L;

    private RuleSet wfc() {
        List<TileType> tiles = List.of(
                new TileType("water", 6.0, true, "WATER", "SAND", true, List.of()),
                new TileType("sand", 4.0, true, "SAND", "SAND", false, List.of()),
                new TileType("grass", 10.0, true, "GRASS_BLOCK", "DIRT", false, List.of()),
                new TileType("cactus", 2.0, false, "SAND", "SAND", false, List.of()));
        Set<AllowedPair> allowed = Set.of(
                new AllowedPair("water", "sand"),
                new AllowedPair("sand", "grass"),
                new AllowedPair("sand", "cactus"),
                new AllowedPair("grass", "cactus"));
        return new RuleSet(tiles, allowed, new GenerationSettings(4, 8, 64, 8, "grass", true));
    }

    private SkyGridConfig sky() {
        List<BlockWeight> blocks = List.of(
                new BlockWeight("STONE", 30), new BlockWeight("DIRT", 20),
                new BlockWeight("GRASS_BLOCK", 15), new BlockWeight("OAK_LOG", 10),
                new BlockWeight("SAND", 8), new BlockWeight("WATER", 5),
                new BlockWeight("IRON_ORE", 4), new BlockWeight("CHEST", 2));
        return new SkyGridConfig(4, 0, 64, 0.03, blocks);
    }

    private GeneratorConfig config(String algorithm) {
        return new GeneratorConfig(algorithm, wfc(), sky());
    }

    private RegionGenerationService region(RuleState state) {
        return new RegionGenerationService(state, new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
    }

    private TreeMap<Integer, String> column(TerrainAlgorithm algorithm, int x, int z) {
        TreeMap<Integer, String> out = new TreeMap<>();
        algorithm.generateColumn(SEED, x, z, 0, 64, out::put);
        return out;
    }

    @Test
    void wfcNeighborsRespectAdjacency() {
        RegionGenerationService region = region(new RuleState(config("wfc"), 4));
        RuleSet rules = wfc();
        for (int cx = -8; cx <= 8; cx++) {
            for (int cz = -8; cz <= 8; cz++) {
                String here = region.tileAt(SEED, cx, cz).id();
                String east = region.tileAt(SEED, cx + 1, cz).id();
                String south = region.tileAt(SEED, cx, cz + 1).id();
                assertTrue(compatible(rules, here, east), here + " next to " + east);
                assertTrue(compatible(rules, here, south), here + " next to " + south);
            }
        }
    }

    @Test
    void factorySelectsAlgorithmByConfig() {
        RuleState state = new RuleState(config("wfc"), 4);
        AlgorithmService algorithms = new AlgorithmService(state, region(state));
        assertEquals("wfc", algorithms.current().id());
        state.apply(config("skygrid"));
        assertEquals("skygrid", algorithms.current().id());
        state.apply(config("noise_skygrid"));
        assertEquals("noise_skygrid", algorithms.current().id());
    }

    @Test
    void independentSkyGridIsSparseAndDeterministic() {
        TerrainAlgorithm sky = new IndependentSkyGridAlgorithm(sky());
        assertTrue(column(sky, 1, 2).isEmpty(), "off-grid column must be void");
        TreeMap<Integer, String> onGrid = column(sky, 8, 12);
        assertEquals(17, onGrid.size(), "y = 0..64 step 4 means 17 blocks");
        assertTrue(onGrid.keySet().stream().allMatch(y -> y % 4 == 0), "blocks only on grid lines");
        assertEquals(onGrid, column(sky, 8, 12), "must be deterministic");
    }

    @Test
    void noiseSkyGridClustersMoreThanIndependent() {
        double independent = neighborSameFraction(new IndependentSkyGridAlgorithm(sky()));
        double noise = neighborSameFraction(new NoiseSkyGridAlgorithm(sky()));
        assertTrue(noise > independent, "value noise should cluster: " + noise + " vs " + independent);
    }

    private double neighborSameFraction(TerrainAlgorithm algorithm) {
        int same = 0;
        int total = 0;
        for (int gx = 0; gx < 20; gx++) {
            for (int gz = 0; gz < 20; gz++) {
                TreeMap<Integer, String> column = column(algorithm, gx * 4, gz * 4);
                List<Integer> ys = List.copyOf(column.keySet());
                for (int i = 0; i + 1 < ys.size(); i++) {
                    total++;
                    if (column.get(ys.get(i)).equals(column.get(ys.get(i + 1)))) {
                        same++;
                    }
                }
            }
        }
        return total == 0 ? 0 : (double) same / total;
    }

    private boolean compatible(RuleSet rules, String a, String b) {
        if (a.equals(b)) {
            return rules.settings().allowSelf();
        }
        return rules.allowed().stream()
                .anyMatch(p -> (p.a().equals(a) && p.b().equals(b)) || (p.a().equals(b) && p.b().equals(a)));
    }
}
