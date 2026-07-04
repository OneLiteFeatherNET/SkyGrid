package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.PhysicsMode;
import net.onelitefeather.skygridng.api.model.PlacementRule;
import net.onelitefeather.skygridng.api.model.Rule;
import net.onelitefeather.skygridng.api.model.RuleCatalog;
import net.onelitefeather.skygridng.api.model.WaterMode;
import net.onelitefeather.skygridng.ports.in.SkyGridDecorations;
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
 * Tests the rule-driven classic decoration pass: a decoration rule lays its ground support, boxes in a
 * contained still-water source (so nothing flows into the void) and grows its plant column, all
 * deterministically from the world seed. MockBukkit supplies the block-data factory; the region is a
 * Mockito mock so we can capture exactly which blocks are placed.
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
        // One certain (probability 1.0) sugar-cane rule: on sand, watered by a contained source, 1 tall.
        Rule cane = new Rule(BlockId.of("minecraft:sugar_cane"), Map.of(), PhysicsMode.NONE,
                new PlacementRule(BlockId.of("minecraft:sand"), false, WaterMode.SOURCE, 1, 1, 1.0));
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
    @DisplayName("a certain rule grows sugar cane on sand with contained water at the grid cell")
    void growsSugarCane() {
        LimitedRegion area = mock(LimitedRegion.class);
        when(area.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(area.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        populator().populate(world(), new Random(0), 0, 0, area);

        // Exactly one grid cell in this chunk (spacing 16): the support (sand) at y=64 and the cane at y=65.
        ArgumentCaptor<BlockData> placed = ArgumentCaptor.forClass(BlockData.class);
        verify(area, atLeastOnce()).setBlockData(anyInt(), anyInt(), anyInt(), placed.capture());
        assertThat(placed.getAllValues()).extracting(BlockData::getMaterial)
                .contains(Material.SAND, Material.SUGAR_CANE);

        // The contained pool is placed as water via setType (StillWater boxes it in).
        verify(area, atLeastOnce()).setType(anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(Material.WATER));
    }

    @Test
    @DisplayName("placement is deterministic for a given seed")
    void deterministic() {
        LimitedRegion first = mock(LimitedRegion.class);
        when(first.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(first.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);
        LimitedRegion second = mock(LimitedRegion.class);
        when(second.isInRegion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(second.getType(anyInt(), anyInt(), anyInt())).thenReturn(Material.AIR);

        populator().populate(world(), new Random(0), 0, 0, first);
        populator().populate(world(), new Random(999), 0, 0, second);

        ArgumentCaptor<Integer> fx = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> fy = ArgumentCaptor.forClass(Integer.class);
        verify(first, atLeastOnce()).setBlockData(fx.capture(), fy.capture(), anyInt(),
                org.mockito.ArgumentMatchers.any(BlockData.class));
        ArgumentCaptor<Integer> sx = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sy = ArgumentCaptor.forClass(Integer.class);
        verify(second, atLeastOnce()).setBlockData(sx.capture(), sy.capture(), anyInt(),
                org.mockito.ArgumentMatchers.any(BlockData.class));

        assertThat(fx.getAllValues()).isEqualTo(sx.getAllValues());
        assertThat(fy.getAllValues()).isEqualTo(sy.getAllValues());
    }
}
