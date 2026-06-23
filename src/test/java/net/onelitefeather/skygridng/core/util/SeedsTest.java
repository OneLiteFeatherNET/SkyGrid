package net.onelitefeather.skygridng.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the deterministic seed derivation. We assert the internal guarantees the
 * generators rely on: range, determinism, and coordinate sensitivity.
 */
class SeedsTest {

    @Test
    @DisplayName("unitDouble always lies in [0, 1)")
    void unitDoubleInRange() {
        for (long s = -5000; s <= 5000; s++) {
            double v = Seeds.unitDouble(s);
            assertThat(v).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
        }
    }

    @Test
    @DisplayName("mix and mix3 are deterministic for equal inputs")
    void mixIsDeterministic() {
        assertThat(Seeds.mix(7L, 1, 2)).isEqualTo(Seeds.mix(7L, 1, 2));
        assertThat(Seeds.mix3(7L, 1, 2, 3)).isEqualTo(Seeds.mix3(7L, 1, 2, 3));
        assertThat(Seeds.blockSeed(9L, 1, 2, 3)).isEqualTo(Seeds.blockSeed(9L, 1, 2, 3));
    }

    @Test
    @DisplayName("mix3 reacts to every coordinate")
    void mix3IsCoordinateSensitive() {
        long base = Seeds.mix3(7L, 1, 2, 3);
        assertThat(Seeds.mix3(7L, 9, 2, 3)).isNotEqualTo(base);
        assertThat(Seeds.mix3(7L, 1, 9, 3)).isNotEqualTo(base);
        assertThat(Seeds.mix3(7L, 1, 2, 9)).isNotEqualTo(base);
    }

    @Test
    @DisplayName("the named seed spaces (block, cell, region, boundary) do not collide")
    void seedNamespacesAreDistinct() {
        long worldSeed = 123L;
        long region = Seeds.regionSeed(worldSeed, 2, 3);
        long cell = Seeds.cellSeed(worldSeed, 2, 3);
        long boundary = Seeds.boundarySeed(worldSeed, 2, 3);
        assertThat(region).isNotEqualTo(cell);
        assertThat(cell).isNotEqualTo(boundary);
        assertThat(region).isNotEqualTo(boundary);
    }

    @Test
    @DisplayName("rng(seed) produces a reproducible sequence")
    void rngIsReproducible() {
        RandomGenerator a = Seeds.rng(42L);
        RandomGenerator b = Seeds.rng(42L);
        for (int i = 0; i < 100; i++) {
            assertThat(a.nextLong()).isEqualTo(b.nextLong());
        }
    }
}
