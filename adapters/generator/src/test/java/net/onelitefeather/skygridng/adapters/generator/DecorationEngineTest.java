package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.LegendEntry;
import net.onelitefeather.skygridng.api.model.PhysicsMode;
import net.onelitefeather.skygridng.api.model.PlacementRule;
import net.onelitefeather.skygridng.api.model.Rule;
import net.onelitefeather.skygridng.api.model.StructurePattern;
import net.onelitefeather.skygridng.ports.in.SkyGridDecorations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the rule-driven classic decoration pass: {@link DecorationEngine} scans the grid cells of a
 * chunk, rolls each decoration rule in order and, on a hit, places its {@link StructurePattern} and
 * grows its plant column; failing that, it falls back to filling an existing chest or spawner.
 * Framework-free: a plain {@link FakeRegionWriter} stands in for the native region (no MockBukkit),
 * and {@link SkyGridDecorations} is mocked with Mockito, same as the old MockBukkit-based test did.
 *
 * <p>With {@code spacing=16, minY=60, maxY=64} and chunk (0, 0) scanned over world y range [0, 319],
 * the grid has exactly one eligible cell: (x=0, y=64, z=0). This was confirmed against the real
 * compiled {@link DecorationEngine#decorateChunk} (and its private {@code firstGridLine} rounding) via
 * a throwaway driver before being relied on here.
 */
class DecorationEngineTest {

    private static final long SEED = 42L;
    private static final BlockId SAND = BlockId.of("minecraft:sand");
    private static final BlockId WATER = BlockId.of("minecraft:water");
    private static final BlockId SANDSTONE = BlockId.of("minecraft:sandstone");
    private static final BlockId SUGAR_CANE = BlockId.of("minecraft:sugar_cane");
    private static final BlockId CHEST = BlockId.of("minecraft:chest");
    private static final BlockId SPAWNER = BlockId.of("minecraft:spawner");

    /**
     * One certain (probability 1.0) sugar-cane rule: an anchor cell on sand, a boxed water source beside
     * it, solid support on the other 3 sides, 1 block tall (min height = max height = 1).
     */
    private static Rule sugarCaneRule() {
        StructurePattern structure = new StructurePattern(
                List.of(List.of(" # ", "SW#", " # ")),
                Map.of(
                        'S', new LegendEntry(SAND, Map.of(), true),
                        'W', new LegendEntry(WATER, Map.of(), false)),
                SANDSTONE);
        return new Rule(SUGAR_CANE, Map.of(), PhysicsMode.NONE, new PlacementRule(structure, 1, 1, 1.0));
    }

    private static SkyGridDecorations decorations() {
        SkyGridDecorations decorations = mock(SkyGridDecorations.class);
        when(decorations.spacing()).thenReturn(16);
        when(decorations.minY()).thenReturn(60);
        when(decorations.maxY()).thenReturn(64);
        when(decorations.lootTableAt(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(decorations.spawnerMobAt(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());
        return decorations;
    }

    @Test
    @DisplayName("a certain rule grows sugar cane with contained water at the one grid cell in the chunk")
    void certainRuleGrowsPlantWithContainedWater() {
        DecorationEngine engine = new DecorationEngine(decorations(), List.of(sugarCaneRule()));
        FakeRegionWriter writer = new FakeRegionWriter();

        engine.decorateChunk(SEED, 0, 319, 0, 0, writer);

        // Confirmed against the real compiled engine: the anchor (sand), the boxed water, the 3
        // ensure_solid walls (sandstone) and the 1-tall cane column, all at the single grid cell.
        assertThat(writer.blockAt(0, 64, 0)).isEqualTo(SAND);
        assertThat(writer.blockAt(1, 64, 0)).isEqualTo(WATER);
        assertThat(writer.blockAt(1, 64, -1)).isEqualTo(SANDSTONE);
        assertThat(writer.blockAt(2, 64, 0)).isEqualTo(SANDSTONE);
        assertThat(writer.blockAt(1, 64, 1)).isEqualTo(SANDSTONE);
        assertThat(writer.blockAt(0, 65, 0)).isEqualTo(SUGAR_CANE);
        assertThat(writer.placements()).hasSize(6);
        assertThat(writer.scheduledUpdates())
                .containsExactly(new FakeRegionWriter.ScheduledUpdate(0, 65, 0, PhysicsMode.NONE));
    }

    @Test
    @DisplayName("two calls with the same seed against fresh writers produce identical placements")
    void deterministicAcrossSeparateCalls() {
        DecorationEngine first = new DecorationEngine(decorations(), List.of(sugarCaneRule()));
        DecorationEngine second = new DecorationEngine(decorations(), List.of(sugarCaneRule()));
        FakeRegionWriter writerA = new FakeRegionWriter();
        FakeRegionWriter writerB = new FakeRegionWriter();

        first.decorateChunk(SEED, 0, 319, 0, 0, writerA);
        second.decorateChunk(SEED, 0, 319, 0, 0, writerB);

        assertThat(writerA.placements()).isEqualTo(writerB.placements());
        assertThat(writerA.scheduledUpdates()).isEqualTo(writerB.scheduledUpdates());
    }

    @Test
    @DisplayName("a structure that does not fit the region consumes the cell without growing or falling back")
    void structureNotFittingConsumesCellWithoutGrowing() {
        SkyGridDecorations decorations = decorations();
        DecorationEngine engine = new DecorationEngine(decorations, List.of(sugarCaneRule()));
        FakeRegionWriter writer = new FakeRegionWriter();
        // The bottom-right ensure_solid cell (dx=1, dz=-1 from the anchor) does not fit.
        writer.excludeFromRegion(1, 64, -1);

        engine.decorateChunk(SEED, 0, 319, 0, 0, writer);

        // Confirmed against the real compiled engine: the rule rolled a hit (consuming the cell) but
        // nothing was written, and the chest/spawner fallback was never probed for this cell.
        assertThat(writer.placements()).isEmpty();
        assertThat(writer.scheduledUpdates()).isEmpty();
        verify(decorations, never()).lootTableAt(anyLong(), anyInt(), anyInt(), anyInt());
        verify(decorations, never()).spawnerMobAt(anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("fills an existing chest with the loot table the catalog picks, when no rule rolls")
    void fillsChestWhenNoRuleRolls() {
        SkyGridDecorations decorations = decorations();
        BlockId lootTable = BlockId.of("minecraft:chests/simple_dungeon");
        when(decorations.lootTableAt(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(Optional.of(lootTable));
        DecorationEngine engine = new DecorationEngine(decorations, List.of()); // no decoration rules at all
        FakeRegionWriter writer = new FakeRegionWriter();
        writer.presetBlock(0, 64, 0, CHEST);

        engine.decorateChunk(SEED, 0, 319, 0, 0, writer);

        // Confirmed against the real compiled engine: fillLootChest is called with the catalog's table
        // and Seeds.blockSeed(seed, x, y, z) for that cell.
        assertThat(writer.lootFills())
                .containsExactly(new FakeRegionWriter.LootFill(0, 64, 0, lootTable, -4672468917220201093L));
        assertThat(writer.spawnerFills()).isEmpty();
    }

    @Test
    @DisplayName("fills an existing spawner with the mob the catalog picks, when no rule rolls")
    void fillsSpawnerWhenNoRuleRolls() {
        SkyGridDecorations decorations = decorations();
        BlockId mob = BlockId.of("minecraft:zombie");
        when(decorations.spawnerMobAt(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(Optional.of(mob));
        DecorationEngine engine = new DecorationEngine(decorations, List.of());
        FakeRegionWriter writer = new FakeRegionWriter();
        writer.presetBlock(0, 64, 0, SPAWNER);

        engine.decorateChunk(SEED, 0, 319, 0, 0, writer);

        assertThat(writer.spawnerFills()).containsExactly(new FakeRegionWriter.SpawnerFill(0, 64, 0, mob));
        assertThat(writer.lootFills()).isEmpty();
    }

    @Test
    @DisplayName("neither chest nor spawner fallback fires on a plain block with no rule rolled")
    void noFallbackOnPlainBlock() {
        SkyGridDecorations decorations = decorations();
        DecorationEngine engine = new DecorationEngine(decorations, List.of());
        FakeRegionWriter writer = new FakeRegionWriter();
        // (0, 64, 0) defaults to air: neither a chest nor a spawner.

        engine.decorateChunk(SEED, 0, 319, 0, 0, writer);

        assertThat(writer.lootFills()).isEmpty();
        assertThat(writer.spawnerFills()).isEmpty();
    }
}
