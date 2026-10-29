package net.onelitefeather.skygridng.generator;

/**
 * A weighted entry in a SkyGrid block table.
 *
 * The material is a platform-neutral {@link BlockId} (for example {@code minecraft:stone}), never a
 * Bukkit material name, so the core stays portable. The adapter resolves it to a native block.
 *
 * @param material the block, a namespaced id resolved by the adapter
 * @param weight   relative frequency, must be greater than zero
 */
public record BlockWeight(BlockId material, double weight) {
    public BlockWeight {
        if (material == null) {
            throw new IllegalArgumentException("BlockWeight.material must not be null");
        }
        if (weight <= 0.0) {
            throw new IllegalArgumentException("BlockWeight.weight must be > 0: " + material);
        }
    }
}
