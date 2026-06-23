package net.onelitefeather.skygridng.testutil;

import net.onelitefeather.skygridng.core.model.AllowedPair;
import net.onelitefeather.skygridng.core.model.BlockWeight;
import net.onelitefeather.skygridng.core.model.GenerationSettings;
import net.onelitefeather.skygridng.core.model.GeneratorConfig;
import net.onelitefeather.skygridng.core.model.RuleSet;
import net.onelitefeather.skygridng.core.model.SkyGridConfig;
import net.onelitefeather.skygridng.core.model.TileType;

import java.util.List;
import java.util.Set;

/**
 * Shared, deterministic fixtures for the test suite. Kept tiny and explicit so each test reads on
 * its own.
 */
public final class Fixtures {

    public static final long SEED = 987654321L;

    private Fixtures() {
    }

    public static List<TileType> tiles() {
        return List.of(
                new TileType("water", 6.0, true, "WATER", "SAND", true, List.of()),
                new TileType("sand", 4.0, true, "SAND", "SAND", false, List.of()),
                new TileType("grass", 10.0, true, "GRASS_BLOCK", "DIRT", false, List.of()),
                new TileType("cactus", 2.0, false, "SAND", "SAND", false, List.of()));
    }

    public static Set<AllowedPair> pairs() {
        return Set.of(
                new AllowedPair("water", "sand"),
                new AllowedPair("sand", "grass"),
                new AllowedPair("sand", "cactus"),
                new AllowedPair("grass", "cactus"));
    }

    public static GenerationSettings settings() {
        return new GenerationSettings(4, 8, 64, 8, "grass", true);
    }

    public static RuleSet ruleSet() {
        return new RuleSet(tiles(), pairs(), settings());
    }

    public static SkyGridConfig skyConfig() {
        return new SkyGridConfig(4, 0, 64, 0.03, List.of(
                new BlockWeight("STONE", 30), new BlockWeight("DIRT", 20),
                new BlockWeight("GRASS_BLOCK", 15), new BlockWeight("OAK_LOG", 10),
                new BlockWeight("SAND", 8), new BlockWeight("WATER", 5),
                new BlockWeight("IRON_ORE", 4), new BlockWeight("CHEST", 2)));
    }

    public static GeneratorConfig config(String algorithm) {
        return new GeneratorConfig(algorithm, ruleSet(), skyConfig());
    }
}
