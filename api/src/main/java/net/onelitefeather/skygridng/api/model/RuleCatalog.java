package net.onelitefeather.skygridng.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All per-block rules, keyed by the block they govern. Loaded from the {@code rules/} directory (one
 * file per block) and shared by every preset: a rule for {@code minecraft:oak_leaves} applies wherever
 * that block is generated. An empty catalog means "no rules", which reproduces plain block placement.
 *
 * @param rules the rules by block id, an immutable ordered map (never {@code null})
 */
public record RuleCatalog(Map<BlockId, Rule> rules) {
    public RuleCatalog {
        rules = rules == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    /** An empty catalog (no rules). */
    public static RuleCatalog empty() {
        return new RuleCatalog(Map.of());
    }

    /** The rule governing {@code block}, if any. */
    public Optional<Rule> forBlock(BlockId block) {
        return Optional.ofNullable(rules.get(block));
    }

    /** The rules that grow a decoration (they carry placement conditions), in their stored order. */
    public List<Rule> decorations() {
        List<Rule> result = new ArrayList<>();
        for (Rule rule : rules.values()) {
            if (rule.isDecoration()) {
                result.add(rule);
            }
        }
        return List.copyOf(result);
    }

    /** Returns a copy with {@code rule} added or replaced. */
    public RuleCatalog with(Rule rule) {
        Map<BlockId, Rule> copy = new LinkedHashMap<>(rules);
        copy.put(rule.block(), rule);
        return new RuleCatalog(copy);
    }
}
