package net.onelitefeather.skygridng.testutil;

import net.onelitefeather.skygridng.api.model.AllowedPair;
import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.GenerationSettings;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.RuleSet;
import net.onelitefeather.skygridng.api.model.SkyGridConfig;
import net.onelitefeather.skygridng.api.model.TileType;

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

    /** A vanilla block id, for example {@code mc("stone")} -> {@code minecraft:stone}. */
    public static BlockId mc(String value) {
        return BlockId.of("minecraft:" + value);
    }

    public static List<TileType> tiles() {
        return List.of(
                new TileType("water", 6.0, true, mc("water"), mc("sand"), true, List.of()),
                new TileType("sand", 4.0, true, mc("sand"), mc("sand"), false, List.of()),
                new TileType("grass", 10.0, true, mc("grass_block"), mc("dirt"), false, List.of()),
                new TileType("cactus", 2.0, false, mc("sand"), mc("sand"), false, List.of()));
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
                new BlockWeight(mc("stone"), 30), new BlockWeight(mc("dirt"), 20),
                new BlockWeight(mc("grass_block"), 15), new BlockWeight(mc("oak_log"), 10),
                new BlockWeight(mc("sand"), 8), new BlockWeight(mc("water"), 5),
                new BlockWeight(mc("iron_ore"), 4), new BlockWeight(mc("chest"), 2)));
    }

    public static GeneratorConfig config(String algorithm) {
        return new GeneratorConfig(algorithm, ruleSet(), skyConfig());
    }
}
