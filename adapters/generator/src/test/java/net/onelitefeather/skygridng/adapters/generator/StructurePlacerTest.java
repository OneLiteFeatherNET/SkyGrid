package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.LegendEntry;
import net.onelitefeather.skygridng.api.model.RuleCatalog;
import net.onelitefeather.skygridng.api.model.StructurePattern;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.generator.LimitedRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * White-box tests for {@link StructurePlacer}: given a {@link StructurePattern} anchored at a world
 * position, it must bounds-check every touched cell before writing anything, then apply
 * {@code ENSURE_SOLID} only to an air cell, {@code CLEAR_AIR} unconditionally, a legend cell with its
 * resolved block data, and never read or write an {@code IGNORE} cell. MockBukkit supplies the real
 * block-data factory (via {@link RuleResolver}); the region is a Mockito mock so exactly which cells
 * are touched can be captured.
 */
class StructurePlacerTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RuleResolver resolver() {
        return new RuleResolver(new RuleCatalog(Map.of()), Material.STONE);
    }

    /**
     * A single-layer, single-row, 4-wide pattern: col0 'S' (anchor, sand), col1 '#' (ensure solid,
     * filler sandstone), col2 'W' (water, with an explicit {@code level=0} state), col3 '.' (clear air).
     */
    private StructurePattern pattern() {
        return new StructurePattern(
                List.of(List.of("S#W.")),
                Map.of(
                        'S', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                        'W', new LegendEntry(BlockId.of("minecraft:water"), Map.of("level", "0"), false)),
                BlockId.of("minecraft:sandstone"));
    }

    @Test
    @DisplayName("ENSURE_SOLID fills an air cell with the filler but leaves an existing block alone")
    void ensureSolidFillsAirButLeavesExistingBlock() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        // col1 (the '#' cell, world x=11) is already solid stone; every other queried cell is air.
        when(area.getType(11, 10, 20)).thenReturn(Material.STONE);
        when(area.getType(10, 10, 20)).thenReturn(Material.AIR);
        when(area.getType(12, 10, 20)).thenReturn(Material.AIR);
        when(area.getType(13, 10, 20)).thenReturn(Material.AIR);

        boolean placed = StructurePlacer.place(area, resolver(), pattern(), 10, 10, 20);

        assertThat(placed).isTrue();
        // The already-solid '#' cell must never be overwritten.
        verify(area, never()).setBlockData(eq(11), eq(10), eq(20), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("ENSURE_SOLID fills the cell with the filler block when it is currently air")
    void ensureSolidFillsAirCellWithFiller() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        StructurePlacer.place(area, resolver(), pattern(), 10, 10, 20);

        ArgumentCaptor<BlockData> captor = ArgumentCaptor.forClass(BlockData.class);
        verify(area).setBlockData(eq(11), eq(10), eq(20), captor.capture());
        assertThat(captor.getValue().getMaterial()).isEqualTo(Material.SANDSTONE);
    }

    @Test
    @DisplayName("CLEAR_AIR clears the cell to air regardless of what was there before")
    void clearAirClearsCellToAir() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);

        StructurePlacer.place(area, resolver(), pattern(), 10, 10, 20);

        // col3 is world x=13: the CLEAR_AIR cell.
        verify(area).setType(13, 10, 20, Material.AIR);
    }

    @Test
    @DisplayName("a legend entry with state places block data carrying that state")
    void legendEntryWithStateAppliesState() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        StructurePlacer.place(area, resolver(), pattern(), 10, 10, 20);

        // col2 is world x=12: the 'W' legend cell (water, level=0).
        ArgumentCaptor<BlockData> captor = ArgumentCaptor.forClass(BlockData.class);
        verify(area).setBlockData(eq(12), eq(10), eq(20), captor.capture());
        BlockData water = captor.getValue();
        assertThat(water.getMaterial()).isEqualTo(Material.WATER);
        assertThat(((Levelled) water).getLevel()).isZero();
    }

    @Test
    @DisplayName("the anchor cell itself places its legend block (sand) at the anchor world position")
    void anchorCellPlacesItsOwnBlock() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        StructurePlacer.place(area, resolver(), pattern(), 10, 10, 20);

        ArgumentCaptor<BlockData> captor = ArgumentCaptor.forClass(BlockData.class);
        verify(area).setBlockData(eq(10), eq(10), eq(20), captor.capture());
        assertThat(captor.getValue().getMaterial()).isEqualTo(Material.SAND);
    }

    @Test
    @DisplayName("a pattern that does not fit the region places nothing and returns false")
    void patternOutsideRegionPlacesNothing() {
        LimitedRegion area = mock(LimitedRegion.class);
        // Every cell fits except the CLEAR_AIR one (world x=13), which is out of the populatable region.
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.isInRegion(13, 10, 20)).thenReturn(false);

        boolean placed = StructurePlacer.place(area, resolver(), pattern(), 10, 10, 20);

        assertThat(placed).isFalse();
        verify(area, never()).setBlockData(anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.any());
        verify(area, never()).setType(anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("IGNORE cells are never read or written, even when out of the region")
    void ignoreCellsAreNeverReadOrWritten() {
        // A pattern with an explicit IGNORE cell (col0) beside the anchor (col1).
        StructurePattern withIgnore = new StructurePattern(
                List.of(List.of(" S")),
                Map.of('S', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true)),
                null);

        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        // Anchor at world (10, 10, 20); the IGNORE cell would be at world (9, 10, 20).
        boolean placed = StructurePlacer.place(area, resolver(), withIgnore, 10, 10, 20);

        assertThat(placed).isTrue();
        verify(area, never()).isInRegion(9, 10, 20);
        verify(area, never()).getType(9, 10, 20);
        verify(area, never()).setBlockData(eq(9), eq(10), eq(20), org.mockito.ArgumentMatchers.any());
        verify(area, never()).setType(eq(9), eq(10), eq(20), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a pattern with only reserved cells needs no legend interaction beyond the anchor block")
    void noInteractionBeyondNecessaryCells() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);

        boolean placed = StructurePlacer.place(area, resolver(), pattern(), 10, 10, 20);

        assertThat(placed).isTrue();
        // 4 cells total; the '#' cell is left alone (already solid) so only 3 writes happen:
        // anchor sand, water legend, clear air.
        verify(area, org.mockito.Mockito.times(2)).setBlockData(anyInt(), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.any());
        verify(area, org.mockito.Mockito.times(1)).setType(anyInt(), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.any());
    }
}
