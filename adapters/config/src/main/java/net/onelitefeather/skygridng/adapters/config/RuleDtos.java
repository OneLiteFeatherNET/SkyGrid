package net.onelitefeather.skygridng.adapters.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Adapter-internal Jackson DTOs for the per-block rule files ({@code rules/<block>.<ext>}). They mirror
 * the on-disk shape one to one: the block id and the enum values are plain strings here (mapped to
 * {@code BlockId}, {@code PhysicsMode} and {@code WaterMode} in {@link JacksonRuleRepository}), so the
 * api and core never see Jackson. A {@code null} field falls back to the rule's neutral default.
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
            String requiresGround,
            Boolean requiresLiquidNeighbor,
            String water,
            Integer minHeight,
            Integer maxHeight,
            Double probability
    ) {
    }
}
