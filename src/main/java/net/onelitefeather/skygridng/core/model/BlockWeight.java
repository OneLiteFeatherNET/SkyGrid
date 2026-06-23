package net.onelitefeather.skygridng.core.model;

/**
 * A weighted entry in a SkyGrid block table.
 *
 * The material is held as a string (for example "STONE") so the core stays free of Bukkit.
 * The Paper adapter resolves the name to a Material.
 *
 * @param material Bukkit material name, resolved by the adapter
 * @param weight   relative frequency, must be greater than zero
 */
public record BlockWeight(String material, double weight) {
    public BlockWeight {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("BlockWeight.material must not be blank");
        }
        if (weight <= 0.0) {
            throw new IllegalArgumentException("BlockWeight.weight must be > 0: " + material);
        }
    }
}
