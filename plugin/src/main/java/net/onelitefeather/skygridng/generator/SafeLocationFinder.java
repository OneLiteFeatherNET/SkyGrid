package net.onelitefeather.skygridng.generator;


import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Framework-free: a SkyBlock/EssentialsX-RTP style safe-block search, driven entirely through
 * {@link GroundReader} instead of a native Bukkit type.
 *
 * A sparse SkyGrid world has no continuous terrain, so a single floating block is valid "ground":
 * {@link GroundReader#highestGroundY(int, int)} finds it directly, no vertical scan needed. The
 * first candidate whose ground is solid and safe, and whose foot and head space is clear, wins.
 */
public final class SafeLocationFinder {

    /** Solid or non-solid blocks that are technically "clear" but unsafe to spawn on or in. */
    private static final BlockId LAVA = BlockId.of("minecraft:lava");
    private static final BlockId FIRE = BlockId.of("minecraft:fire");
    private static final BlockId SOUL_FIRE = BlockId.of("minecraft:soul_fire");
    private static final BlockId MAGMA_BLOCK = BlockId.of("minecraft:magma_block");
    private static final BlockId CACTUS = BlockId.of("minecraft:cactus");
    private static final BlockId SWEET_BERRY_BUSH = BlockId.of("minecraft:sweet_berry_bush");
    private static final BlockId WITHER_ROSE = BlockId.of("minecraft:wither_rose");
    private static final BlockId POWDER_SNOW = BlockId.of("minecraft:powder_snow");
    private static final BlockId NETHER_PORTAL = BlockId.of("minecraft:nether_portal");
    private static final BlockId END_PORTAL = BlockId.of("minecraft:end_portal");
    private static final BlockId END_GATEWAY = BlockId.of("minecraft:end_gateway");
    private static final BlockId CAMPFIRE = BlockId.of("minecraft:campfire");
    private static final BlockId SOUL_CAMPFIRE = BlockId.of("minecraft:soul_campfire");

    private static final Set<BlockId> UNSAFE = Set.of(
            LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS,
            SWEET_BERRY_BUSH, WITHER_ROSE, POWDER_SNOW, NETHER_PORTAL,
            END_PORTAL, END_GATEWAY, CAMPFIRE, SOUL_CAMPFIRE);

    private SafeLocationFinder() {
    }

    /** A found safe landing spot, in world block coordinates. */
    public record Spot(int x, int y, int z) {
    }

    /**
     * Searches for a safe, standable location around {@code (centerX, centerZ)}.
     *
     * Attempt 0 probes the exact center; every later attempt probes a point offset by
     * {@code rng.nextInt(-searchRadius, searchRadius + 1)} on both axes, so the same rng (and thus the
     * same seed) always retries the same candidate sequence.
     */
    public static Optional<Spot> find(GroundReader ground, int centerX, int centerZ, int searchRadius,
                                       int maxAttempts, RandomGenerator rng) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = centerX;
            int z = centerZ;
            if (attempt > 0) {
                x += rng.nextInt(-searchRadius, searchRadius + 1);
                z += rng.nextInt(-searchRadius, searchRadius + 1);
            }
            Optional<Spot> candidate = probe(ground, x, z);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<Spot> probe(GroundReader ground, int x, int z) {
        OptionalInt groundY = ground.highestGroundY(x, z);
        if (groundY.isEmpty()) {
            return Optional.empty(); // void column, not worth reading blocks
        }
        int y = groundY.getAsInt();
        if (!isSafeGround(ground, x, y, z) || !isSafeSpace(ground, x, y + 1, z) || !isSafeSpace(ground, x, y + 2, z)) {
            return Optional.empty();
        }
        return Optional.of(new Spot(x, y + 1, z));
    }

    private static boolean isSafeGround(GroundReader ground, int x, int y, int z) {
        return ground.isSolid(x, y, z) && !UNSAFE.contains(ground.blockAt(x, y, z));
    }

    private static boolean isSafeSpace(GroundReader ground, int x, int y, int z) {
        return ground.isPassable(x, y, z) && !UNSAFE.contains(ground.blockAt(x, y, z));
    }
}
