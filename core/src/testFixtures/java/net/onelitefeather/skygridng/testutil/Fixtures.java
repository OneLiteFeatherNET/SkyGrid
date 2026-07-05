package net.onelitefeather.skygridng.testutil;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.api.model.SkyGridConfig;
import net.onelitefeather.skygridng.core.algorithm.IndependentSkyGridAlgorithm;
import net.onelitefeather.skygridng.core.algorithm.NoiseSkyGridAlgorithm;
import net.onelitefeather.skygridng.core.service.PresetResolver;
import net.onelitefeather.skygridng.ports.algorithm.AlgorithmFactory;
import net.onelitefeather.skygridng.ports.out.PresetRepository;

import java.util.List;
import java.util.Map;

/**
 * Shared, deterministic fixtures for the test suite. Kept tiny and explicit so each test reads on
 * its own.
 */
public final class Fixtures {

    public static final long SEED = 987654321L;

    private Fixtures() {
    }

    /** The two shipped algorithm factories, the same set the composition root binds via MapBinder. */
    public static Map<String, AlgorithmFactory> defaultAlgorithmFactories() {
        return Map.of(
                IndependentSkyGridAlgorithm.ID, config -> new IndependentSkyGridAlgorithm(config.skygrid()),
                NoiseSkyGridAlgorithm.ID, config -> new NoiseSkyGridAlgorithm(config.skygrid()));
    }

    /** A vanilla block id, for example {@code mc("stone")} -> {@code minecraft:stone}. */
    public static BlockId mc(String value) {
        return BlockId.of("minecraft:" + value);
    }

    public static SkyGridConfig skyConfig() {
        return new SkyGridConfig(4, 0, 64, 0.03,
                List.of(
                        new BlockWeight(mc("stone"), 30), new BlockWeight(mc("dirt"), 20),
                        new BlockWeight(mc("grass_block"), 15), new BlockWeight(mc("oak_log"), 10),
                        new BlockWeight(mc("sand"), 8), new BlockWeight(mc("water"), 5),
                        new BlockWeight(mc("iron_ore"), 4), new BlockWeight(mc("chest"), 2)),
                List.of(new BlockWeight(mc("chests/simple_dungeon"), 4), new BlockWeight(mc("chests/jungle_temple"), 1)),
                List.of(new BlockWeight(mc("zombie"), 3), new BlockWeight(mc("blaze"), 1)));
    }

    public static GeneratorConfig config(String algorithm) {
        return new GeneratorConfig(algorithm, skyConfig());
    }

    /** A single-preset catalog ("classic") flattening {@link #config(String)}; active is "classic". */
    public static PresetCatalog presetCatalog(String algorithm) {
        return new PresetCatalog(Map.of("classic", PresetResolver.toPreset("classic", config(algorithm))), "classic");
    }

    /** An in-memory {@link PresetRepository} for tests, capturing the last saved catalog. */
    public static final class InMemoryPresetRepository implements PresetRepository {
        private PresetCatalog catalog;

        public InMemoryPresetRepository(PresetCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public PresetCatalog load() {
            return catalog;
        }

        @Override
        public void save(PresetCatalog catalog) {
            this.catalog = catalog;
        }
    }
}
