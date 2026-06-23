package net.onelitefeather.skygridng.core.algorithm;

import net.onelitefeather.skygridng.core.service.RegionGenerationService;
import net.onelitefeather.skygridng.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.skygridng.core.solver.WeightedRandomCollapse;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the WFC surface terrain algorithm. It writes a solid column up to the surface and
 * tops it with the surface block of the WFC tile at that cell.
 */
class WfcSurfaceAlgorithmTest {

    private WfcSurfaceAlgorithm algorithm() {
        RuleState state = new RuleState(Fixtures.config("wfc"), 4);
        RegionGenerationService region = new RegionGenerationService(
                state, new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
        return new WfcSurfaceAlgorithm(region);
    }

    @Test
    @DisplayName("id is wfc and it requests the decoration populator")
    void contract() {
        WfcSurfaceAlgorithm algorithm = algorithm();
        assertThat(algorithm.id()).isEqualTo("wfc");
        assertThat(algorithm.usesWfcDecorations()).isTrue();
    }

    @Test
    @DisplayName("the column is solid from the floor up to the surface")
    void columnIsSolidUpToSurface() {
        WfcSurfaceAlgorithm algorithm = algorithm();
        TreeMap<Integer, String> column = new TreeMap<>();
        algorithm.generateColumn(Fixtures.SEED, 10, 12, 0, 200, column::put);

        assertThat(column.firstKey()).isZero();
        assertThat(column.lastKey()).isEqualTo(64); // surfaceY from the fixture settings
        assertThat(column).hasSize(65);
        for (int y = column.firstKey(); y <= column.lastKey(); y++) {
            assertThat(column).containsKey(y); // contiguous, no gaps
        }
    }

    @Test
    @DisplayName("the top block is the surface material of the WFC tile at that cell")
    void surfaceMatchesRegionTile() {
        RuleState state = new RuleState(Fixtures.config("wfc"), 4);
        RegionGenerationService region = new RegionGenerationService(
                state, new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
        WfcSurfaceAlgorithm algorithm = new WfcSurfaceAlgorithm(region);

        TreeMap<Integer, String> column = new TreeMap<>();
        algorithm.generateColumn(Fixtures.SEED, 10, 12, 0, 200, column::put);

        // cellSize is 4, so world (10,12) maps to cell (2,3).
        String expected = region.tileAt(Fixtures.SEED, 2, 3).surface();
        assertThat(column.get(64)).isEqualTo(expected);
    }
}
