package net.onelitefeather.skygridng.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for {@link StructurePlacer}: given a {@link StructurePattern} anchored at a world
 * position, it must bounds-check every touched cell before writing anything, then apply
 * {@code ENSURE_SOLID} only to an air cell, {@code CLEAR_AIR} unconditionally, a legend cell with its
 * resolved block and state, and never read or write an {@code IGNORE} cell. Framework-free: a plain
 * {@link FakeRegionWriter} stands in for the native region, no MockBukkit involved.
 */
class StructurePlacerTest {

    private static final BlockId SAND = BlockId.of("minecraft:sand");
    private static final BlockId SANDSTONE = BlockId.of("minecraft:sandstone");
    private static final BlockId WATER = BlockId.of("minecraft:water");
    private static final BlockId AIR = BlockId.of("minecraft:air");
    private static final BlockId STONE = BlockId.of("minecraft:stone");

    /**
     * A single-layer, single-row, 4-wide pattern: col0 'S' (anchor, sand), col1 '#' (ensure solid,
     * filler sandstone), col2 'W' (water, with an explicit {@code level=0} state), col3 '.' (clear air).
     * Anchored at (10, 10, 20), the world positions are x=10..13 (verified against the real compiled
     * {@link StructurePattern#anchorOffset()}/{@code offsetFrom} logic): col c maps to world x = 10 + c.
     */
    private StructurePattern pattern() {
        return new StructurePattern(
                List.of(List.of("S#W.")),
                Map.of(
                        'S', new LegendEntry(SAND, Map.of(), true),
                        'W', new LegendEntry(WATER, Map.of("level", "0"), false)),
                SANDSTONE);
    }

    @Test
    @DisplayName("ENSURE_SOLID fills an air cell with the filler but leaves an existing block alone")
    void ensureSolidFillsAirButLeavesExistingBlock() {
        FakeRegionWriter writer = new FakeRegionWriter();
        // col1 (the '#' cell, world x=11) is already solid stone; every other cell defaults to air.
        writer.presetBlock(11, 10, 20, STONE);

        boolean placed = StructurePlacer.place(writer, pattern(), 10, 10, 20);

        assertThat(placed).isTrue();
        assertThat(writer.blockAt(11, 10, 20)).isEqualTo(STONE);
        assertThat(writer.placements()).noneMatch(p -> p.x() == 11 && p.y() == 10 && p.z() == 20);
    }

    @Test
    @DisplayName("ENSURE_SOLID fills the cell with the filler block when it is currently air")
    void ensureSolidFillsAirCellWithFiller() {
        FakeRegionWriter writer = new FakeRegionWriter();

        boolean placed = StructurePlacer.place(writer, pattern(), 10, 10, 20);

        assertThat(placed).isTrue();
        assertThat(writer.blockAt(11, 10, 20)).isEqualTo(SANDSTONE);
    }

    @Test
    @DisplayName("CLEAR_AIR clears the cell to air regardless of what was there before")
    void clearAirClearsCellUnconditionally() {
        FakeRegionWriter writer = new FakeRegionWriter();
        writer.presetBlock(13, 10, 20, STONE);

        boolean placed = StructurePlacer.place(writer, pattern(), 10, 10, 20);

        assertThat(placed).isTrue();
        // col3 is world x=13: the CLEAR_AIR cell.
        assertThat(writer.blockAt(13, 10, 20)).isEqualTo(AIR);
    }

    @Test
    @DisplayName("a legend entry with state places the block with that state")
    void legendEntryWithStateAppliesState() {
        FakeRegionWriter writer = new FakeRegionWriter();

        StructurePlacer.place(writer, pattern(), 10, 10, 20);

        // col2 is world x=12: the 'W' legend cell (water, level=0).
        assertThat(writer.blockAt(12, 10, 20)).isEqualTo(WATER);
        assertThat(writer.stateAt(12, 10, 20)).isEqualTo(Map.of("level", "0"));
    }

    @Test
    @DisplayName("the anchor cell itself places its own legend block (sand) at the anchor world position")
    void anchorCellPlacesItsOwnBlock() {
        FakeRegionWriter writer = new FakeRegionWriter();

        StructurePlacer.place(writer, pattern(), 10, 10, 20);

        assertThat(writer.blockAt(10, 10, 20)).isEqualTo(SAND);
    }

    @Test
    @DisplayName("a pattern that does not fit the region places nothing and returns false")
    void patternOutsideRegionPlacesNothing() {
        FakeRegionWriter writer = new FakeRegionWriter();
        // Every cell fits except the CLEAR_AIR one (world x=13), which is out of the populatable region.
        writer.excludeFromRegion(13, 10, 20);

        boolean placed = StructurePlacer.place(writer, pattern(), 10, 10, 20);

        assertThat(placed).isFalse();
        assertThat(writer.placements()).isEmpty();
    }

    @Test
    @DisplayName("IGNORE cells are never read or written, even when out of the region")
    void ignoreCellsAreNeverReadOrWritten() {
        // A pattern with an explicit IGNORE cell (col0) beside the anchor (col1).
        StructurePattern withIgnore = new StructurePattern(
                List.of(List.of(" S")),
                Map.of('S', new LegendEntry(SAND, Map.of(), true)),
                null);
        FakeRegionWriter writer = new FakeRegionWriter();
        // Mark the IGNORE cell as out of region: if StructurePlacer ever bounds-checked it, this would
        // flip the result to false. Its coordinate must never be read at all.
        writer.excludeFromRegion(9, 10, 20);

        // Anchor at world (10, 10, 20); the IGNORE cell would be at world (9, 10, 20).
        boolean placed = StructurePlacer.place(writer, withIgnore, 10, 10, 20);

        assertThat(placed).isTrue();
        assertThat(writer.isInRegionCalls()).doesNotContain(new FakeRegionWriter.Coord(9, 10, 20));
        assertThat(writer.blockAtCalls()).doesNotContain(new FakeRegionWriter.Coord(9, 10, 20));
        assertThat(writer.placements()).noneMatch(p -> p.x() == 9 && p.y() == 10 && p.z() == 20);
    }

    @Test
    @DisplayName("a pattern with reserved cells needs no extra interaction beyond the necessary writes")
    void noInteractionBeyondNecessaryCells() {
        FakeRegionWriter writer = new FakeRegionWriter();
        // Every queried cell is already solid stone, so the ENSURE_SOLID cell (world x=11) is left alone.
        writer.presetBlock(10, 10, 20, STONE);
        writer.presetBlock(11, 10, 20, STONE);
        writer.presetBlock(12, 10, 20, STONE);
        writer.presetBlock(13, 10, 20, STONE);

        boolean placed = StructurePlacer.place(writer, pattern(), 10, 10, 20);

        assertThat(placed).isTrue();
        // 4 cells total; the '#' cell is left alone (already solid) so only 3 writes happen: anchor
        // sand, water legend, clear air.
        assertThat(writer.placements()).hasSize(3);
        assertThat(writer.placements()).noneMatch(p -> p.x() == 11 && p.y() == 10 && p.z() == 20);
    }
}
