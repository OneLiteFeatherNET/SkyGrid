package net.onelitefeather.skygridng.api.model;

/**
 * A monster spawner decoration: a spawner block configured with a mob type.
 *
 * The mob is a namespaced entity id (for example {@code minecraft:zombie} or {@code minecraft:blaze});
 * the adapter resolves it to a spawned type, so a SkyGrid map can offer blaze rods (and thus the End)
 * or villagers through configurable spawners.
 *
 * @param block       the spawner block (for example {@code minecraft:spawner})
 * @param mob         the spawned entity id
 * @param probability spawn probability per column in [0,1]
 */
public record Spawner(
        BlockId block,
        BlockId mob,
        double probability
) implements Decoration {
    public Spawner {
        if (block == null || mob == null) {
            throw new IllegalArgumentException("Spawner.block and mob must not be null");
        }
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in [0,1]: " + mob);
        }
    }
}
