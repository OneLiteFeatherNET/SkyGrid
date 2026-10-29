package net.onelitefeather.skygridng.generator;


import java.util.List;
import java.util.Optional;

/**
 * The standard {@link SkyGridDecorations}: picks a loot table for a chest and a mob for a spawner
 * deterministically from the configured weighted lists, rebuilt when the configuration changes
 * (Observer on {@link RuleState}). The pick uses a salted block seed, so loot and mob choices are
 * independent of each other and of the block sampling, yet reproducible per coordinate.
 */
public final class DefaultSkyGridDecorations implements SkyGridDecorations {

    private static final long LOOT_SALT = 0x9E3779B97F4A7C15L;
    private static final long MOB_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long PLANT_SALT = 0x2545F4914F6CDD1DL;

    /**
     * Self-contained plants scattered on the classic grid. A sugar cane oasis (sand support + its own
     * water + cane) so water and sugar cane appear where the sparse grid can never place them adjacent.
     */
    private static final List<Plant> PLANTS = List.of(
            new Plant(BlockId.of("minecraft:sugar_cane"), false, BlockId.of("minecraft:sand"), 0.04, 1, 3, true));

    private final RuleState state;
    private volatile SkyGridConfig config;
    private volatile WeightedTable lootTable;   // null when no chest loot is configured
    private volatile WeightedTable mobTable;     // null when no spawner mobs are configured

    public DefaultSkyGridDecorations(RuleState state) {
        this.state = state;
        rebuild();
        state.addChangeListener(this::rebuild);
    }

    private void rebuild() {
        SkyGridConfig current = state.current().skygrid();
        this.config = current;
        this.lootTable = current.chestLoot().isEmpty() ? null : WeightedTable.of(current.chestLoot());
        this.mobTable = current.spawnerMobs().isEmpty() ? null : WeightedTable.of(current.spawnerMobs());
    }

    @Override
    public boolean active() {
        return lootTable != null || mobTable != null || !PLANTS.isEmpty();
    }

    @Override
    public int spacing() {
        return config.spacing();
    }

    @Override
    public int minY() {
        return config.minY();
    }

    @Override
    public int maxY() {
        return config.maxY();
    }

    @Override
    public Optional<BlockId> lootTableAt(long seed, int x, int y, int z) {
        WeightedTable table = lootTable;
        if (table == null) {
            return Optional.empty();
        }
        return Optional.of(table.sample(Seeds.unitDouble(Seeds.blockSeed(seed ^ LOOT_SALT, x, y, z))));
    }

    @Override
    public Optional<BlockId> spawnerMobAt(long seed, int x, int y, int z) {
        WeightedTable table = mobTable;
        if (table == null) {
            return Optional.empty();
        }
        return Optional.of(table.sample(Seeds.unitDouble(Seeds.blockSeed(seed ^ MOB_SALT, x, y, z))));
    }

    @Override
    public Optional<Plant> plantAt(long seed, int x, int y, int z) {
        double roll = Seeds.unitDouble(Seeds.blockSeed(seed ^ PLANT_SALT, x, y, z));
        double cumulative = 0.0;
        for (Plant plant : PLANTS) {
            cumulative += plant.probability();
            if (roll < cumulative) {
                return Optional.of(plant);
            }
        }
        return Optional.empty();
    }
}
