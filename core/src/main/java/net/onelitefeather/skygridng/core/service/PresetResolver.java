package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.BiomeId;
import net.onelitefeather.skygridng.api.model.BlockWeight;
import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.api.model.Preset;
import net.onelitefeather.skygridng.api.model.PresetCatalog;
import net.onelitefeather.skygridng.api.model.RuleSet;
import net.onelitefeather.skygridng.api.model.SkyGridConfig;
import net.onelitefeather.skygridng.api.model.SpawnConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Flattens a preset's {@code extends} chain into a complete {@link GeneratorConfig}.
 *
 * Resolution flattens the preset's {@code extends} graph (a post-order walk so every preset comes
 * after its parents), then applies overrides in that order so the most derived preset wins (the same
 * rule as CMake presets and Minecraft world presets). A preset may extend several parents; they are
 * applied left to right, so a later parent overrides an earlier one. A field left unset anywhere
 * falls back to a built-in default; the block palette and the WFC rule set have no universal
 * default, so a graph that never supplies them is rejected. Inheritance cycles (across the whole
 * graph, not just one chain) and unknown parents are rejected too. Pure and platform-neutral.
 */
public final class PresetResolver {

    private PresetResolver() {
    }

    /** Resolves the named preset against the catalog into a complete generator config. */
    public static GeneratorConfig resolve(PresetCatalog catalog, String name) {
        String algorithm = "skygrid";
        Integer spacing = 4;
        Integer minY = 0;
        Integer maxY = 64;
        Double noiseScale = 0.03;
        List<BlockWeight> blocks = null;
        List<BlockWeight> chestLoot = List.of();
        List<BlockWeight> spawnerMobs = List.of();
        List<BiomeId> biomes = GeneratorConfig.DEFAULT_BIOMES;
        SpawnConfig spawn = SpawnConfig.defaults();
        RuleSet wfc = null;

        for (Preset p : flatten(catalog, name)) { // ancestors first, target last (most derived wins)
            if (p.algorithm() != null) algorithm = p.algorithm();
            if (p.spacing() != null) spacing = p.spacing();
            if (p.minY() != null) minY = p.minY();
            if (p.maxY() != null) maxY = p.maxY();
            if (p.noiseScale() != null) noiseScale = p.noiseScale();
            if (p.blocks() != null) blocks = p.blocks();
            if (p.chestLoot() != null) chestLoot = p.chestLoot();
            if (p.spawnerMobs() != null) spawnerMobs = p.spawnerMobs();
            if (p.biomes() != null) biomes = p.biomes();
            if (p.spawn() != null) spawn = p.spawn();
            if (p.wfc() != null) wfc = p.wfc();
        }

        if (blocks == null) {
            throw new IllegalStateException("preset '" + name + "' has no block palette (set 'blocks' in it or a base)");
        }
        if (wfc == null) {
            throw new IllegalStateException("preset '" + name + "' has no wfc rule set (set 'wfc' in it or a base)");
        }
        SkyGridConfig sky = new SkyGridConfig(spacing, minY, maxY, noiseScale, blocks, chestLoot, spawnerMobs);
        return new GeneratorConfig(algorithm, wfc, sky, spawn, biomes);
    }

    /** Captures the resolved config as a self-contained preset (no parents), for persisting edits. */
    public static Preset toPreset(String name, GeneratorConfig config) {
        SkyGridConfig sky = config.skygrid();
        return new Preset(name, List.of(), config.algorithm(),
                sky.spacing(), sky.minY(), sky.maxY(), sky.noiseScale(),
                sky.blocks(), sky.chestLoot(), sky.spawnerMobs(),
                config.biomes(), config.spawn(), config.wfc());
    }

    /**
     * Flattens the extends graph into apply order: a post-order walk so every preset appears after
     * all its parents and exactly once. Rejects cycles (over the whole graph) and unknown parents.
     */
    private static List<Preset> flatten(PresetCatalog catalog, String name) {
        List<Preset> order = new ArrayList<>();
        Set<String> added = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        visit(catalog, name, order, added, path);
        return order;
    }

    private static void visit(PresetCatalog catalog, String name, List<Preset> order,
                              Set<String> added, Deque<String> path) {
        if (added.contains(name)) {
            return; // already placed together with all its ancestors
        }
        if (path.contains(name)) {
            throw new IllegalStateException("preset inheritance cycle through '" + name + "'");
        }
        Preset preset = catalog.presets().get(name);
        if (preset == null) {
            throw new IllegalStateException("unknown preset '" + name + "'");
        }
        path.push(name);
        for (String parent : preset.parents()) {
            visit(catalog, parent, order, added, path);
        }
        path.pop();
        order.add(preset);
        added.add(name);
    }
}
