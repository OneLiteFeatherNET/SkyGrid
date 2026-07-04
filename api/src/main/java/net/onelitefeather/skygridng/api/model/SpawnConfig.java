package net.onelitefeather.skygridng.api.model;

/**
 * Immutable spread-spawn configuration.
 *
 * On a shared SkyGrid server every player should start on their own little platform, far enough
 * from the others that they never see each other on first join (the shared-but-separate SkyBlock
 * feel). Spawn points are laid out on an outward square spiral, {@code spacing} blocks apart, and a
 * small platform of {@code platformBlock} is built so the player never falls into the void.
 *
 * @param spread         whether spread-spawn is active (false keeps the vanilla world spawn)
 * @param spacing        blocks between adjacent spawn points (must be large enough to stay out of view)
 * @param platformY      the Y level the starter platform is built at
 * @param platformBlock  the block the starter platform is made of (namespaced id)
 * @param platformRadius the platform extends this many blocks around the centre (0 = single block)
 * @param villager       whether a starter villager is placed on the platform (so trading is reachable)
 */
public record SpawnConfig(
        boolean spread,
        int spacing,
        int platformY,
        BlockId platformBlock,
        int platformRadius,
        boolean villager
) {
    public SpawnConfig {
        if (spacing < 1) {
            throw new IllegalArgumentException("spawn spacing must be >= 1");
        }
        if (platformRadius < 0) {
            throw new IllegalArgumentException("spawn platformRadius must be >= 0");
        }
        if (platformBlock == null) {
            throw new IllegalArgumentException("spawn platformBlock must not be null");
        }
    }

    /** The defaults used when a config omits the spawn section: spread on, platforms 512 blocks apart. */
    public static SpawnConfig defaults() {
        return new SpawnConfig(true, 512, 64, BlockId.of("minecraft:cobblestone"), 2, true);
    }
}
