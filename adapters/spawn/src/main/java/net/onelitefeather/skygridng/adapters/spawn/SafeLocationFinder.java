package net.onelitefeather.skygridng.adapters.spawn;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * In adapter: a SkyBlock/EssentialsX-RTP style safe-block search.
 *
 * A sparse SkyGrid world has no continuous terrain, so a single floating block is valid "ground":
 * {@link World#getHighestBlockYAt(int, int, HeightMap)} with {@link HeightMap#MOTION_BLOCKING} finds
 * it directly, no vertical scan needed. The first candidate whose ground is solid and safe, and whose
 * foot and head space is clear, wins.
 */
final class SafeLocationFinder {

    /** Solid or non-solid blocks that are technically "clear" but unsafe to spawn on or in. */
    private static final Set<Material> UNSAFE = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POWDER_SNOW, Material.NETHER_PORTAL,
            Material.END_PORTAL, Material.END_GATEWAY, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    private SafeLocationFinder() {
    }

    /**
     * Searches for a safe, standable location around {@code (centerX, centerZ)}.
     *
     * Attempt 0 probes the exact center; every later attempt probes a point offset by
     * {@code rng.nextInt(-searchRadius, searchRadius + 1)} on both axes, so the same rng (and thus the
     * same seed) always retries the same candidate sequence.
     */
    static Optional<Location> find(World world, int centerX, int centerZ, int searchRadius, int maxAttempts,
                                    RandomGenerator rng) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = centerX;
            int z = centerZ;
            if (attempt > 0) {
                x += rng.nextInt(-searchRadius, searchRadius + 1);
                z += rng.nextInt(-searchRadius, searchRadius + 1);
            }
            Optional<Location> candidate = probe(world, x, z);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<Location> probe(World world, int x, int z) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (groundY <= world.getMinHeight()) {
            return Optional.empty(); // void column, not worth reading blocks
        }
        Block ground = world.getBlockAt(x, groundY, z);
        Block foot = world.getBlockAt(x, groundY + 1, z);
        Block head = world.getBlockAt(x, groundY + 2, z);
        if (!isSafeGround(ground) || !isSafeSpace(foot) || !isSafeSpace(head)) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x + 0.5, groundY + 1.0, z + 0.5));
    }

    private static boolean isSafeGround(Block block) {
        return block.getType().isSolid() && !UNSAFE.contains(block.getType());
    }

    private static boolean isSafeSpace(Block block) {
        return block.isPassable() && !UNSAFE.contains(block.getType());
    }
}
