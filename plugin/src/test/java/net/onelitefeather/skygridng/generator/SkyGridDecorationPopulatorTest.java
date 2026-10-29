package net.onelitefeather.skygridng.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thin wiring test for {@link SkyGridDecorationPopulator}: it must build a fresh
 * {@link LimitedRegionWriter} wrapping the populator's {@link LimitedRegion} and the shared
 * {@link RuleResolver}, and delegate to a real {@link net.onelitefeather.skygridng.generator.DecorationEngine}
 * end to end. This does NOT re-test the engine's decoration logic (that is covered white-box, without
 * Bukkit, in {@code adapters.generator}'s own {@code DecorationEngineTest}); it only proves the Bukkit
 * shell constructs the writer and calls through correctly for one simple scenario. MockBukkit supplies
 * the Material registry and block-data factory the writer needs; the region and world are Mockito
 * mocks, same as this suite's pre-refactor version.
 */
class SkyGridDecorationPopulatorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SkyGridDecorationPopulator populator() {
        // One certain (probability 1.0) sugar-cane rule: on sand, boxed by a contained source, 1 tall.
        StructurePattern structure = new StructurePattern(
                List.of(List.of(" # ", "SW#", " # ")),
                Map.of(
                        'S', new LegendEntry(BlockId.of("minecraft:sand"), Map.of(), true),
                        'W', new LegendEntry(BlockId.of("minecraft:water"), Map.of(), false)),
                BlockId.of("minecraft:sandstone"));
        Rule cane = new Rule(BlockId.of("minecraft:sugar_cane"), Map.of(), PhysicsMode.NONE,
                new PlacementRule(structure, 1, 1, 1.0));
        Map<BlockId, Rule> rules = new LinkedHashMap<>();
        rules.put(cane.block(), cane);
        RuleResolver resolver = new RuleResolver(new RuleCatalog(rules), Material.STONE);

        SkyGridDecorations decorations = mock(SkyGridDecorations.class);
        when(decorations.spacing()).thenReturn(16);
        when(decorations.minY()).thenReturn(60);
        when(decorations.maxY()).thenReturn(64);
        when(decorations.lootTableAt(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(decorations.spawnerMobAt(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());
        return new SkyGridDecorationPopulator(decorations, resolver);
    }

    private WorldInfo world() {
        WorldInfo world = mock(WorldInfo.class);
        when(world.getSeed()).thenReturn(42L);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }

    @Test
    @DisplayName("populate builds a LimitedRegionWriter and grows sugar cane through the real engine")
    void populateDelegatesToDecorationEngine() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        populator().populate(world(), new Random(0), 0, 0, area);

        // The shell wired the writer and the engine correctly if the real sand/water/cane placement
        // that DecorationEngineTest verifies white-box actually reaches the native region.
        ArgumentCaptor<BlockData> placed = ArgumentCaptor.forClass(BlockData.class);
        verify(area, atLeastOnce()).setBlockData(anyInt(), anyInt(), anyInt(), placed.capture());
        assertThat(placed.getAllValues()).extracting(BlockData::getMaterial)
                .contains(Material.SAND, Material.WATER, Material.SUGAR_CANE, Material.SANDSTONE);
    }
}
