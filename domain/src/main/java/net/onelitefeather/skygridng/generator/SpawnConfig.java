package net.onelitefeather.skygridng.generator;

/**
 * Immutable spread-spawn configuration.
 *
 * On a shared SkyGrid server every player should start far enough from the others that they never
 * see each other on first join (the shared-but-separate SkyBlock feel). Spawn points are laid out
 * on an outward square spiral, {@code spacing} blocks apart. Because the SkyGrid algorithms leave
 * most columns as void, the adapter does not build a platform: it searches outward from the
 * assigned slot for a safe, standable block and teleports the player there instead.
 *
 * @param spread       whether spread-spawn is active (false keeps the vanilla world spawn)
 * @param spacing      blocks between adjacent spawn points (must be large enough to stay out of view)
 * @param searchRadius blocks around the assigned slot to probe when searching for a safe block
 * @param maxAttempts  candidate columns to try before giving up the safe-block search
 */
public record SpawnConfig(
        boolean spread,
        int spacing,
        int searchRadius,
        int maxAttempts
) {
    public SpawnConfig {
        if (spacing < 1) {
            throw new IllegalArgumentException("spawn spacing must be >= 1");
        }
        if (searchRadius < 0) {
            throw new IllegalArgumentException("spawn searchRadius must be >= 0");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("spawn maxAttempts must be >= 1");
        }
    }

    /** The defaults used when a config omits the spawn section: spread on, platforms 512 blocks apart. */
    public static SpawnConfig defaults() {
        return new SpawnConfig(true, 512, 16, 32);
    }
}
