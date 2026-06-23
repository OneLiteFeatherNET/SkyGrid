package net.onelitefeather.skygridng.core.solver;

import net.onelitefeather.skygridng.core.model.AdjacencyRules;
import net.onelitefeather.skygridng.core.model.Direction;
import net.onelitefeather.skygridng.core.model.RuleSet;
import net.onelitefeather.skygridng.core.model.TileType;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the WFC solver: valid output, determinism, presets, and fallback.
 */
class WfcSolverTest {

    private static final int W = 6;
    private static final int H = 6;
    private static final Direction EAST = Direction.values()[0];

    private final WfcSolver solver = new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse());

    @Test
    @DisplayName("a solvable grid yields valid indices whose neighbours respect adjacency")
    void solutionRespectsAdjacency() {
        RuleSet rules = Fixtures.ruleSet();
        AdjacencyRules adjacency = rules.buildAdjacency();
        List<TileType> tiles = rules.tiles();
        int[] grid = solver.solve(W, H, 123L, adjacency, tiles, rules.fullMask(), 8,
                rules.tileIndex("grass"), (x, z) -> rules.fullMask());

        assertThat(grid).hasSize(W * H);
        for (int value : grid) {
            assertThat(value).isBetween(0, tiles.size() - 1);
        }
        for (int z = 0; z < H; z++) {
            for (int x = 0; x + 1 < W; x++) {
                int here = grid[z * W + x];
                int east = grid[z * W + x + 1];
                assertThat((adjacency.support(1L << here, EAST) >> east) & 1L)
                        .as("tile %d next to %d", here, east).isEqualTo(1L);
            }
        }
    }

    @Test
    @DisplayName("the same inputs solve to the same grid")
    void deterministic() {
        RuleSet rules = Fixtures.ruleSet();
        AdjacencyRules adjacency = rules.buildAdjacency();
        int[] a = solver.solve(W, H, 7L, adjacency, rules.tiles(), rules.fullMask(), 8,
                rules.tileIndex("grass"), (x, z) -> rules.fullMask());
        int[] b = solver.solve(W, H, 7L, adjacency, rules.tiles(), rules.fullMask(), 8,
                rules.tileIndex("grass"), (x, z) -> rules.fullMask());
        assertThat(a).containsExactly(b);
    }

    @Test
    @DisplayName("a preset pins a border cell to a fixed tile")
    void presetPinsBorder() {
        RuleSet rules = Fixtures.ruleSet();
        int water = rules.tileIndex("water");
        int[] grid = solver.solve(W, H, 5L, rules.buildAdjacency(), rules.tiles(), rules.fullMask(), 8,
                rules.tileIndex("grass"),
                (x, z) -> (x == 0 && z == 0) ? (1L << water) : rules.fullMask());
        assertThat(grid[0]).isEqualTo(water);
    }

    @Test
    @DisplayName("an unsolvable grid falls back to the fallback tile everywhere")
    void contradictionFallsBack() {
        List<TileType> tiles = Fixtures.tiles();
        // No allowed pairs and no self adjacency is unsatisfiable for a multi cell grid.
        AdjacencyRules impossible = AdjacencyRules.from(tiles, Set.of(), false);
        RuleSet rules = Fixtures.ruleSet();
        int fallback = rules.tileIndex("grass");
        int[] grid = solver.solve(3, 3, 1L, impossible, tiles, rules.fullMask(), 3, fallback,
                (x, z) -> rules.fullMask());
        assertThat(grid).containsOnly(fallback);
    }
}
