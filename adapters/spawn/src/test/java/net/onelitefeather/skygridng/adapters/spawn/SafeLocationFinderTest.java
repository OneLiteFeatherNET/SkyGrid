package net.onelitefeather.skygridng.adapters.spawn;

import net.onelitefeather.skygridng.api.util.Seeds;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * White-box tests for the package-private safe-block search. A Mockito {@link World} lets each test
 * control exactly which columns are solid ground and which are open air, without depending on Paper's
 * real terrain generation; MockBukkit only supplies the {@link Material} registry that the finder's
 * static {@code UNSAFE} set is built from at class load time.
 */
class SafeLocationFinderTest {

    private static final int MIN_HEIGHT = -64;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private World world() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(MIN_HEIGHT);
        return world;
    }

    /** Stubs a solid, safe column: ground at {@code groundY}, open air for the two blocks above it. */
    private void safeColumn(World world, int x, int z, int groundY) {
        when(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING)).thenReturn(groundY);
        stubBlock(world, x, groundY, z, Material.STONE, false);
        stubBlock(world, x, groundY + 1, z, Material.AIR, true);
        stubBlock(world, x, groundY + 2, z, Material.AIR, true);
    }

    /** Stubs a column whose ground material is in the unsafe set (lava). */
    private void unsafeGroundColumn(World world, int x, int z, int groundY) {
        when(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING)).thenReturn(groundY);
        stubBlock(world, x, groundY, z, Material.LAVA, false);
        stubBlock(world, x, groundY + 1, z, Material.AIR, true);
        stubBlock(world, x, groundY + 2, z, Material.AIR, true);
    }

    private void stubBlock(World world, int x, int y, int z, Material material, boolean passable) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(passable);
        when(world.getBlockAt(x, y, z)).thenReturn(block);
    }

    @Test
    @DisplayName("a safe center column is returned on attempt 0, without consulting the rng")
    void safeCenterFoundOnFirstAttempt() {
        World world = world();
        safeColumn(world, 0, 0, 10);
        RandomGenerator rng = mock(RandomGenerator.class);

        Optional<Location> found = SafeLocationFinder.find(world, 0, 0, 10, 3, rng);

        assertThat(found).isPresent();
        Location location = found.orElseThrow();
        assertThat(location.getWorld()).isSameAs(world);
        assertThat(location.getX()).isEqualTo(0.5);
        assertThat(location.getY()).isEqualTo(11.0);
        assertThat(location.getZ()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("an unsafe center falls back to a nearby column the rng picks within the search radius")
    void unsafeCenterFallsBackToNearbySafeColumn() {
        World world = world();
        unsafeGroundColumn(world, 0, 0, 10);
        safeColumn(world, 5, -5, 20);
        RandomGenerator rng = mock(RandomGenerator.class);
        when(rng.nextInt(-10, 11)).thenReturn(5, -5);

        Optional<Location> found = SafeLocationFinder.find(world, 0, 0, 10, 3, rng);

        assertThat(found).contains(new Location(world, 5.5, 21.0, -4.5));
    }

    @Test
    @DisplayName("a ground block in the unsafe material set is rejected")
    void unsafeGroundMaterialIsRejected() {
        World world = world();
        unsafeGroundColumn(world, 0, 0, 10);
        RandomGenerator rng = mock(RandomGenerator.class);

        Optional<Location> found = SafeLocationFinder.find(world, 0, 0, 10, 1, rng);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("a blocked foot space is rejected even on otherwise solid, safe ground")
    void blockedFootIsRejected() {
        World world = world();
        int groundY = 10;
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING)).thenReturn(groundY);
        stubBlock(world, 0, groundY, 0, Material.STONE, false);
        stubBlock(world, 0, groundY + 1, 0, Material.STONE, false); // foot: solid, not passable
        stubBlock(world, 0, groundY + 2, 0, Material.AIR, true);
        RandomGenerator rng = mock(RandomGenerator.class);

        Optional<Location> found = SafeLocationFinder.find(world, 0, 0, 10, 1, rng);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("a blocked head space is rejected even with a clear foot space")
    void blockedHeadIsRejected() {
        World world = world();
        int groundY = 10;
        when(world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING)).thenReturn(groundY);
        stubBlock(world, 0, groundY, 0, Material.STONE, false);
        stubBlock(world, 0, groundY + 1, 0, Material.AIR, true);
        stubBlock(world, 0, groundY + 2, 0, Material.STONE, false); // head: solid, not passable
        RandomGenerator rng = mock(RandomGenerator.class);

        Optional<Location> found = SafeLocationFinder.find(world, 0, 0, 10, 1, rng);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("every attempt exhausted (void columns everywhere) returns empty")
    void exhaustedAttemptsReturnsEmpty() {
        World world = world();
        // Every column reports ground at or below minHeight: a void column, rejected before any block read.
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING))).thenReturn(MIN_HEIGHT);
        RandomGenerator rng = Seeds.rng(42L);

        Optional<Location> found = SafeLocationFinder.find(world, 0, 0, 10, 5, rng);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("the search is deterministic: the same world and rng seed produce the same result")
    void deterministicAcrossRepeatedCalls() {
        World world = world();
        unsafeGroundColumn(world, 0, 0, 10);
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                safeColumn(world, dx, dz, 30);
            }
        }
        long seed = 123456789L;

        Optional<Location> first = SafeLocationFinder.find(world, 0, 0, 10, 5, Seeds.rng(seed));
        Optional<Location> second = SafeLocationFinder.find(world, 0, 0, 10, 5, Seeds.rng(seed));

        assertThat(first).isPresent();
        assertThat(first).isEqualTo(second);
    }
}
