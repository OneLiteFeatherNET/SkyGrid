package net.onelitefeather.skygridng.core.algorithm;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.ports.algorithm.TerrainAlgorithm;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the two SkyGrid algorithms. White-box: the grid math (spacing, range). Black-box:
 * the {@link TerrainAlgorithm} contract (id, determinism, void off grid).
 */
class SkyGridAlgorithmsTest {

    private TreeMap<Integer, BlockId> column(TerrainAlgorithm algorithm, int x, int z) {
        TreeMap<Integer, BlockId> out = new TreeMap<>();
        algorithm.generateColumn(Fixtures.SEED, x, z, 0, 64, out::put);
        return out;
    }

    @Test
    @DisplayName("ids are stable and neither variant uses the WFC decoration populator")
    void contract() {
        TerrainAlgorithm independent = new IndependentSkyGridAlgorithm(Fixtures.skyConfig());
        TerrainAlgorithm noise = new NoiseSkyGridAlgorithm(Fixtures.skyConfig());
        assertThat(independent.id()).isEqualTo("skygrid");
        assertThat(noise.id()).isEqualTo("noise_skygrid");
        assertThat(independent.usesWfcDecorations()).isFalse();
        assertThat(noise.usesWfcDecorations()).isFalse();
    }

    @Test
    @DisplayName("columns off the grid are pure void")
    void offGridIsVoid() {
        TerrainAlgorithm independent = new IndependentSkyGridAlgorithm(Fixtures.skyConfig());
        TerrainAlgorithm noise = new NoiseSkyGridAlgorithm(Fixtures.skyConfig());
        assertThat(column(independent, 1, 2)).isEmpty();
        assertThat(column(noise, 2, 3)).isEmpty();
    }

    @Test
    @DisplayName("on grid columns place one block per grid line and are deterministic")
    void onGridIsSparseAndDeterministic() {
        TerrainAlgorithm independent = new IndependentSkyGridAlgorithm(Fixtures.skyConfig());
        TreeMap<Integer, BlockId> col = column(independent, 8, 12);
        assertThat(col).hasSize(17); // y = 0..64 step 4
        assertThat(col.keySet()).allMatch(y -> y % 4 == 0);
        assertThat(col).isEqualTo(column(independent, 8, 12)); // deterministic
    }

    @Test
    @DisplayName("the noise variant clusters neighbouring blocks more than the independent one")
    void noiseClustersMore() {
        double independent = neighbourSameFraction(new IndependentSkyGridAlgorithm(Fixtures.skyConfig()));
        double noise = neighbourSameFraction(new NoiseSkyGridAlgorithm(Fixtures.skyConfig()));
        assertThat(noise).isGreaterThan(independent);
    }

    private double neighbourSameFraction(TerrainAlgorithm algorithm) {
        int same = 0;
        int total = 0;
        for (int gx = 0; gx < 20; gx++) {
            for (int gz = 0; gz < 20; gz++) {
                TreeMap<Integer, BlockId> col = column(algorithm, gx * 4, gz * 4);
                List<Integer> ys = List.copyOf(col.keySet());
                for (int i = 0; i + 1 < ys.size(); i++) {
                    total++;
                    if (col.get(ys.get(i)).equals(col.get(ys.get(i + 1)))) {
                        same++;
                    }
                }
            }
        }
        return total == 0 ? 0 : (double) same / total;
    }
}
