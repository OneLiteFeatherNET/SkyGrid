package net.onelitefeather.skygridng.generator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a non-reserved symbol in a {@link StructurePattern} grid means: the block to place, optional
 * block-data state overrides, and whether this symbol marks the pattern's anchor cell.
 *
 * <p>The {@code state} map follows the same convention as {@link Rule#state()}: block-data properties
 * as plain strings ({@code {"waterlogged":"true"}}). The adapter turns the block id plus this map into
 * a native block state, so the api and core stay platform-neutral.
 *
 * @param block  the block this symbol places, a namespaced id (never {@code null})
 * @param state  block-data properties to apply, an immutable ordered map (never {@code null}; empty
 *               means "place the default state of the block")
 * @param anchor true when this symbol marks the pattern's anchor cell, the cell whose world position
 *               the whole structure is placed relative to (the same cell today's {@code requiresGround}
 *               targeted); exactly one legend entry across a {@link StructurePattern} may be an anchor
 */
public record LegendEntry(
        BlockId block,
        Map<String, String> state,
        boolean anchor
) {
    public LegendEntry {
        if (block == null) {
            throw new IllegalArgumentException("LegendEntry.block must not be null");
        }
        state = state == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }
}
