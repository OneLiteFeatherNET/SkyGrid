package net.onelitefeather.gridwfc.core.util;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Deterministic seed derivation (SplitMix64) and RNG creation.
 *
 * The same inputs always yield the same seed, independent of thread and order. Uses
 * {@link RandomGenerator}; the chosen algorithm is deterministic over its seed.
 */
public final class Seeds {

    private Seeds() {
    }

    public static long mix(long seed, int a, int b) {
        long h = seed;
        h = splitMix64(h ^ (a * 0x9E3779B97F4A7C15L));
        h = splitMix64(h ^ (b * 0x27D4EB2F165667C5L));
        return splitMix64(h);
    }

    public static long mix3(long seed, int a, int b, int c) {
        long h = seed;
        h = splitMix64(h ^ (a * 0x9E3779B97F4A7C15L));
        h = splitMix64(h ^ (b * 0x27D4EB2F165667C5L));
        h = splitMix64(h ^ (c * 0x165667B19E3779F9L));
        return splitMix64(h);
    }

    public static long regionSeed(long worldSeed, int regionX, int regionZ) {
        return mix(worldSeed ^ 0x5245474_94F4EL, regionX, regionZ);
    }

    public static long cellSeed(long worldSeed, int cellX, int cellZ) {
        return mix(worldSeed ^ 0x43454C4CL, cellX, cellZ);
    }

    public static long boundarySeed(long worldSeed, int cellX, int cellZ) {
        return mix(worldSeed ^ 0x424F554EL, cellX, cellZ);
    }

    /** Deterministic seed for a single 3D block position (SkyGrid). */
    public static long blockSeed(long worldSeed, int x, int y, int z) {
        return mix3(worldSeed ^ 0x424C4F434BL, x, y, z);
    }

    public static RandomGenerator rng(long seed) {
        // L64X128MixRandom is deterministic over its seed and faster/better than java.util.Random.
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    /** A uniform double in [0,1) derived directly from a seed (no RNG object). */
    public static double unitDouble(long seed) {
        long bits = splitMix64(seed) >>> 11; // 53 bits
        return bits * 0x1.0p-53;
    }

    private static long splitMix64(long x) {
        long z = x + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
