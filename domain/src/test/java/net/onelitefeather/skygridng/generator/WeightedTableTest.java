package net.onelitefeather.skygridng.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the cumulative weighted sampler used by the SkyGrid algorithms.
 */
class WeightedTableTest {

    private static BlockId bid(String value) {
        return BlockId.of(value);
    }

    private WeightedTable table() {
        return WeightedTable.of(List.of(
                new BlockWeight(bid("a"), 1), new BlockWeight(bid("b"), 1), new BlockWeight(bid("c"), 2)));
    }

    @Test
    @DisplayName("size and total weight reflect the input")
    void sizeAndTotal() {
        WeightedTable t = table();
        assertThat(t.size()).isEqualTo(3);
        assertThat(t.totalWeight()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("rolls map to the right cumulative bucket and edges are clamped")
    void sampleBuckets() {
        WeightedTable t = table(); // cumulative: a=0.25, b=0.50, c=1.00
        assertThat(t.sample(0.0)).isEqualTo(bid("a"));
        assertThat(t.sample(-1.0)).isEqualTo(bid("a"));   // negative clamps to first
        assertThat(t.sample(0.24)).isEqualTo(bid("a"));
        assertThat(t.sample(0.40)).isEqualTo(bid("b"));
        assertThat(t.sample(0.99)).isEqualTo(bid("c"));
        assertThat(t.sample(1.0)).isEqualTo(bid("c"));     // >=1 clamps to last
    }

    @Test
    @DisplayName("a single entry table always returns that entry")
    void singleEntry() {
        WeightedTable t = WeightedTable.of(List.of(new BlockWeight(bid("only"), 5)));
        assertThat(t.sample(0.0)).isEqualTo(bid("only"));
        assertThat(t.sample(0.999999)).isEqualTo(bid("only"));
    }

    @Test
    @DisplayName("the empirical distribution matches the weights")
    void distributionMatchesWeights() {
        WeightedTable t = table();
        int[] count = new int[3];
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            double roll = Seeds.unitDouble(Seeds.mix(1L, i, i));
            count[t.sample(roll).value().charAt(0) - 'a']++;
        }
        assertThat(count[0] / (double) n).isCloseTo(0.25, org.assertj.core.data.Offset.offset(0.02));
        assertThat(count[1] / (double) n).isCloseTo(0.25, org.assertj.core.data.Offset.offset(0.02));
        assertThat(count[2] / (double) n).isCloseTo(0.50, org.assertj.core.data.Offset.offset(0.02));
    }
}
