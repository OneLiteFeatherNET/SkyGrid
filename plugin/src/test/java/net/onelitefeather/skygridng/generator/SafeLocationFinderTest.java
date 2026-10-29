package net.onelitefeather.skygridng.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the framework-free safe-block search, against a hand-written
 * {@link FakeGroundReader} instead of a Bukkit {@code World}: this module is framework-free by
 * architecture rule, so there is no Bukkit or MockBukkit here at all.
 */
class SafeLocationFinderTest {

    private static final BlockId STONE = BlockId.of("minecraft:stone");
    private static final BlockId AIR = BlockId.of("minecraft:air");
    private static final BlockId LAVA = BlockId.of("minecraft:lava");

    /** An rng that fails the test if consulted at all, for scenarios resolved on attempt 0. */
    private static final RandomGenerator NEVER_CALLED = new RandomGenerator() {
        @Override
        public long nextLong() {
            throw new AssertionError("rng should not be consulted");
        }

        @Override
        public int nextInt(int origin, int bound) {
            throw new AssertionError("rng should not be consulted");
        }
    };

    /** Stubs a column whose ground material is in the unsafe set (lava), open air above it. */
    private void unsafeGroundColumn(FakeGroundReader ground, int x, int z, int groundY) {
        ground.presetGround(x, z, groundY);
        ground.presetBlock(x, groundY, z, LAVA, true, false);
        ground.presetBlock(x, groundY + 1, z, AIR, false, true);
        ground.presetBlock(x, groundY + 2, z, AIR, false, true);
    }

    @Test
    @DisplayName("a safe center column is returned on attempt 0, without consulting the rng")
    void safeCenterFoundOnFirstAttempt() {
        FakeGroundReader ground = new FakeGroundReader();
        ground.safeColumn(0, 0, 10, STONE);

        Optional<SafeLocationFinder.Spot> found = SafeLocationFinder.find(ground, 0, 0, 10, 3, NEVER_CALLED);

        assertThat(found).contains(new SafeLocationFinder.Spot(0, 11, 0));
    }

    @Test
    @DisplayName("an unsafe center falls back to a nearby column the rng picks within the search radius")
    void unsafeCenterFallsBackToNearbySafeColumn() {
        FakeGroundReader ground = new FakeGroundReader();
        unsafeGroundColumn(ground, 0, 0, 10);
        ground.safeColumn(5, -5, 20, STONE);
        RandomGenerator rng = new RandomGenerator() {
            private final int[] values = {5, -5};
            private int index = 0;

            @Override
            public long nextLong() {
                throw new AssertionError("only nextInt(origin, bound) is used by the search");
            }

            @Override
            public int nextInt(int origin, int bound) {
                return values[index++];
            }
        };

        Optional<SafeLocationFinder.Spot> found = SafeLocationFinder.find(ground, 0, 0, 10, 3, rng);

        assertThat(found).contains(new SafeLocationFinder.Spot(5, 21, -5));
    }

    @Test
    @DisplayName("a ground block in the unsafe material set is rejected")
    void unsafeGroundMaterialIsRejected() {
        FakeGroundReader ground = new FakeGroundReader();
        unsafeGroundColumn(ground, 0, 0, 10);

        Optional<SafeLocationFinder.Spot> found = SafeLocationFinder.find(ground, 0, 0, 10, 1, NEVER_CALLED);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("a blocked foot space is rejected even on otherwise solid, safe ground")
    void blockedFootIsRejected() {
        FakeGroundReader ground = new FakeGroundReader();
        int groundY = 10;
        ground.presetGround(0, 0, groundY);
        ground.presetBlock(0, groundY, 0, STONE, true, false);
        ground.presetBlock(0, groundY + 1, 0, STONE, true, false); // foot: solid, not passable
        ground.presetBlock(0, groundY + 2, 0, AIR, false, true);

        Optional<SafeLocationFinder.Spot> found = SafeLocationFinder.find(ground, 0, 0, 10, 1, NEVER_CALLED);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("a blocked head space is rejected even with a clear foot space")
    void blockedHeadIsRejected() {
        FakeGroundReader ground = new FakeGroundReader();
        int groundY = 10;
        ground.presetGround(0, 0, groundY);
        ground.presetBlock(0, groundY, 0, STONE, true, false);
        ground.presetBlock(0, groundY + 1, 0, AIR, false, true);
        ground.presetBlock(0, groundY + 2, 0, STONE, true, false); // head: solid, not passable

        Optional<SafeLocationFinder.Spot> found = SafeLocationFinder.find(ground, 0, 0, 10, 1, NEVER_CALLED);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("every attempt exhausted (void columns everywhere) returns empty")
    void exhaustedAttemptsReturnsEmpty() {
        // Nothing is preset: every column is a void column by default, rejected before any block read.
        FakeGroundReader ground = new FakeGroundReader();
        RandomGenerator rng = Seeds.rng(42L);

        Optional<SafeLocationFinder.Spot> found = SafeLocationFinder.find(ground, 0, 0, 10, 5, rng);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("the search is deterministic: the same ground and rng seed produce the same result")
    void deterministicAcrossRepeatedCalls() {
        FakeGroundReader ground = new FakeGroundReader();
        unsafeGroundColumn(ground, 0, 0, 10);
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ground.safeColumn(dx, dz, 30, STONE);
            }
        }
        long seed = 123456789L;

        Optional<SafeLocationFinder.Spot> first = SafeLocationFinder.find(ground, 0, 0, 10, 5, Seeds.rng(seed));
        Optional<SafeLocationFinder.Spot> second = SafeLocationFinder.find(ground, 0, 0, 10, 5, Seeds.rng(seed));

        assertThat(first).isPresent();
        assertThat(first).isEqualTo(second);
    }
}
