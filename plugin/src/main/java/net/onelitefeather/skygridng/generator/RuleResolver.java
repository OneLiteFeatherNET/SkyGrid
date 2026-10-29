package net.onelitefeather.skygridng.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a platform-neutral {@link BlockId} to the Bukkit {@link BlockData} the generator should
 * place, applying the block's per-block {@link Rule}: its {@code state} map (for example
 * {@code persistent=true} leaves or a {@code level=0} water source) becomes a native block state, and
 * its {@link PhysicsMode} becomes a scheduled update (or none, applied by {@link LimitedRegionWriter}).
 * The core speaks ids and neutral rule intent; this is the one place that intent is turned into Bukkit
 * vocabulary.
 *
 * <p>Resolved block data is cached per id and treated as read only: {@code setBlockData} copies it, so
 * the cached instance is safe to hand out repeatedly; a caller that needs to mutate a property (for
 * example to waterlog a support) must {@link BlockData#clone()} first.
 */
final class RuleResolver {

    private final RuleCatalog rules;
    private final MaterialResolver materials;
    private final ConcurrentHashMap<BlockId, BlockData> cache = new ConcurrentHashMap<>();

    RuleResolver(RuleCatalog rules, Material fallback) {
        this.rules = rules;
        this.materials = new MaterialResolver(fallback);
    }

    /** The block's native material (for reading and comparing existing blocks). */
    Material material(BlockId id) {
        return materials.resolve(id);
    }

    /** The rules that grow a decoration, in catalog order. */
    List<Rule> decorations() {
        return rules.decorations();
    }

    /** The rule governing {@code id}, if any. */
    Optional<Rule> rule(BlockId id) {
        return rules.forBlock(id);
    }

    /** The physics mode of the block's rule, or {@link PhysicsMode#NONE} when it has no rule. */
    PhysicsMode physics(BlockId id) {
        return rules.forBlock(id).map(Rule::physics).orElse(PhysicsMode.NONE);
    }

    /** The block's data with its rule state applied, cached. Clone before mutating a property. */
    BlockData blockData(BlockId id) {
        return cache.computeIfAbsent(id, this::build);
    }

    /**
     * The block's data with an explicit state map applied instead of its rule's own (used by
     * {@code StructurePlacer} for a {@link net.onelitefeather.skygridng.generator.LegendEntry} that
     * carries its own state overrides). Falls back to the plain default state on an invalid property,
     * same as {@link #blockData(BlockId)}. Not cached: a legend's state is typically distinct per rule.
     */
    BlockData blockData(BlockId id, Map<String, String> state) {
        if (state.isEmpty()) {
            return blockData(id);
        }
        try {
            return Bukkit.createBlockData(blockState(id, state));
        } catch (IllegalArgumentException ignored) {
            // an invalid property for this block: fall back to the plain default state
            return materials.resolve(id).createBlockData();
        }
    }

    private BlockData build(BlockId id) {
        Material material = materials.resolve(id);
        Optional<Rule> rule = rules.forBlock(id);
        if (rule.isPresent() && rule.get().hasState()) {
            try {
                return Bukkit.createBlockData(blockState(id, rule.get().state()));
            } catch (IllegalArgumentException ignored) {
                // an invalid property for this block: fall back to the plain default state
            }
        }
        return material.createBlockData();
    }

    private static String blockState(BlockId id, Map<String, String> state) {
        StringBuilder sb = new StringBuilder(id.asString()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : state.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
