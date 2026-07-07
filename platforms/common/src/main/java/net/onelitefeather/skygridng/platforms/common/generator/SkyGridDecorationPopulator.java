package net.onelitefeather.skygridng.platforms.common.generator;

import net.onelitefeather.skygridng.adapters.generator.DecorationEngine;
import net.onelitefeather.skygridng.ports.in.SkyGridDecorations;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Thin Bukkit shell around the framework-free {@link DecorationEngine}: builds a fresh
 * {@link LimitedRegionWriter} wrapping the populator's {@link LimitedRegion} and the shared
 * {@link RuleResolver}, then delegates the actual decoration decisions to the engine.
 *
 * The engine scans only the grid cells the algorithm can place a block on (x/z on grid lines, y on
 * grid layers), so the cost is a few hundred lookups per chunk, not a full volume scan.
 */
public final class SkyGridDecorationPopulator extends BlockPopulator {

    private final RuleResolver rules;
    private final DecorationEngine engine;

    public SkyGridDecorationPopulator(SkyGridDecorations decorations, RuleResolver rules) {
        this.rules = rules;
        this.engine = new DecorationEngine(decorations, rules.decorations());
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion area) {
        long seed = worldInfo.getSeed();
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight() - 1;
        engine.decorateChunk(seed, minY, maxY, chunkX, chunkZ, new LimitedRegionWriter(area, rules));
    }
}
