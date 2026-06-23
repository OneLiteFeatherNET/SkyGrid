package net.onelitefeather.skygridng.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the 3D value noise that drives the clustered SkyGrid.
 */
class ValueNoiseTest {

    @Test
    @DisplayName("samples stay within [0, 1)")
    void sampleInRange() {
        ValueNoise noise = new ValueNoise(42L);
        for (int i = 0; i < 5000; i++) {
            double v = noise.sample(i * 0.13, i * 0.07, i * 0.19);
            assertThat(v).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
        }
    }

    @Test
    @DisplayName("integer lattice points are stable and reproducible")
    void latticeIsStable() {
        ValueNoise noise = new ValueNoise(7L);
        assertThat(noise.sample(3, 4, 5)).isEqualTo(noise.sample(3, 4, 5));
        assertThat(new ValueNoise(7L).sample(3, 4, 5)).isEqualTo(noise.sample(3, 4, 5));
    }

    @Test
    @DisplayName("the field is continuous: a tiny step yields a tiny change")
    void fieldIsContinuous() {
        ValueNoise noise = new ValueNoise(7L);
        double delta = Math.abs(noise.sample(10, 10, 10) - noise.sample(10.01, 10, 10));
        assertThat(delta).isLessThan(0.05);
    }

    @Test
    @DisplayName("different seeds produce different fields")
    void seedsDiffer() {
        ValueNoise a = new ValueNoise(1L);
        ValueNoise b = new ValueNoise(2L);
        boolean anyDifferent = false;
        for (int i = 0; i < 50 && !anyDifferent; i++) {
            if (a.sample(i + 0.5, 0.5, 0.5) != b.sample(i + 0.5, 0.5, 0.5)) {
                anyDifferent = true;
            }
        }
        assertThat(anyDifferent).isTrue();
    }
}
