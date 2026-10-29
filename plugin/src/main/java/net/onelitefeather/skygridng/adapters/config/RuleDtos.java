package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Adapter-internal Jackson DTOs for the per-block rule files ({@code rules/<block>.<ext>}). They mirror
 * the on-disk shape one to one: the block id and the enum values are plain strings here (mapped to
 * {@code BlockId} and {@code PhysicsMode} in {@link JacksonRuleRepository}), so the api and core never
 * see Jackson. A {@code null} field falls back to the rule's neutral default.
 */
final class RuleDtos {

    private RuleDtos() {
    }

    /** One per-block rule: the block, its data state, its physics and optional placement. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RuleDto(
            String block,
            Map<String, String> state,
            String physics,
            PlacementDto placement
    ) {
    }

    /** The placement conditions for a decoration rule (a grown plant column). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PlacementDto(
            StructureDto structure,
            Integer minHeight,
            Integer maxHeight,
            Double probability
    ) {
    }

    /**
     * The declarative 3D footprint placed around the decoration's anchor cell. {@code layers} is the
     * Y-bottom-to-top list of Z-row lists of X-symbol strings, {@code legend} maps every non-reserved
     * symbol used in {@code layers} to its meaning, and {@code filler} is the block used for a
     * {@code '#'} cell that is currently air.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StructureDto(
            String filler,
            Map<String, LegendEntryDto> legend,
            List<List<String>> layers
    ) {
    }

    /** What a single legend symbol means: the block it places, optional state overrides, and anchor. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LegendEntryDto(
            String block,
            Map<String, String> state,
            Boolean anchor
    ) {
    }
}
