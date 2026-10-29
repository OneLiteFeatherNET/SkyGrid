package net.onelitefeather.skygridng.generator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A per-block generation rule: exactly one rule per block, so each block's placement is controlled on
 * its own. A rule pins the block's data state (for example {@code persistent=true} leaves that never
 * decay, or a {@code level=0} water source), how it reacts to block updates ({@link PhysicsMode}), and,
 * when the block is a decoration, the conditions under which it is grown ({@link PlacementRule}).
 *
 * <p>The {@code state} map holds block-data properties as plain strings ({@code {"persistent":"true"}},
 * {@code {"waterlogged":"true"}}, {@code {"level":"0"}}). The adapter turns the block id plus this map
 * into a native block state, so the api and core stay platform-neutral and never mention a Bukkit type.
 *
 * @param block     the block this rule governs, a namespaced id (never {@code null})
 * @param state     block-data properties to apply, an immutable ordered map (never {@code null}; empty
 *                  means "place the default state of the block")
 * @param physics   how the placed block reacts to block updates (never {@code null})
 * @param placement the decoration placement conditions, or {@code null} when the rule only modifies a
 *                  palette block's state and physics
 */
public record Rule(
        BlockId block,
        Map<String, String> state,
        PhysicsMode physics,
        PlacementRule placement
) {
    public Rule {
        if (block == null) {
            throw new IllegalArgumentException("Rule.block must not be null");
        }
        state = state == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(state));
        physics = physics == null ? PhysicsMode.NONE : physics;
    }

    /** True when this rule grows a decoration (it has placement conditions). */
    public boolean isDecoration() {
        return placement != null;
    }

    /** True when this rule pins any block-data property. */
    public boolean hasState() {
        return !state.isEmpty();
    }
}
