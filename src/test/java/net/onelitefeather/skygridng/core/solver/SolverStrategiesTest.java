package net.onelitefeather.skygridng.core.solver;

import net.onelitefeather.skygridng.core.model.TileType;
import net.onelitefeather.skygridng.core.util.Seeds;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the two pluggable WFC strategies (Strategy pattern).
 */
class SolverStrategiesTest {

    private final List<TileType> tiles = Fixtures.tiles();

    @Test
    @DisplayName("Shannon entropy is zero for a settled cell and grows with the domain")
    void entropyOrdering() {
        ShannonEntropyHeuristic heuristic = new ShannonEntropyHeuristic();
        assertThat(heuristic.entropy(1L << 2, tiles)).isEqualTo(0.0);
        double two = heuristic.entropy(0b0011L, tiles);
        double full = heuristic.entropy(0b1111L, tiles);
        assertThat(two).isGreaterThan(0.0);
        assertThat(full).isGreaterThan(two);
    }

    @Test
    @DisplayName("weighted collapse only ever returns a tile that is in the domain mask")
    void collapseStaysInsideTheMask() {
        WeightedRandomCollapse collapse = new WeightedRandomCollapse();
        RandomGenerator random = Seeds.rng(7L);
        long mask = 0b1010L; // sand(1) and cactus(3)
        for (int i = 0; i < 2000; i++) {
            int idx = collapse.collapse(mask, tiles, random);
            assertThat((mask >> idx) & 1L).as("index %d must be in mask", idx).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("a single bit domain collapses to exactly that tile")
    void collapseSingleBit() {
        WeightedRandomCollapse collapse = new WeightedRandomCollapse();
        assertThat(collapse.collapse(1L << 3, tiles, Seeds.rng(1L))).isEqualTo(3);
    }

    @Test
    @DisplayName("the collapse distribution roughly follows the tile weights")
    void collapseDistribution() {
        WeightedRandomCollapse collapse = new WeightedRandomCollapse();
        RandomGenerator random = Seeds.rng(99L);
        long mask = 0b0101L; // water(weight 6) and grass(weight 10), total 16
        int grass = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            if (collapse.collapse(mask, tiles, random) == 2) {
                grass++;
            }
        }
        assertThat(grass / (double) n).isCloseTo(10.0 / 16.0, Offset.offset(0.02));
    }
}
