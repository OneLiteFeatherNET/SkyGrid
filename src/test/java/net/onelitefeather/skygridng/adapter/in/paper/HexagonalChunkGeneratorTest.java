package net.onelitefeather.skygridng.adapter.in.paper;

import net.onelitefeather.skygridng.core.service.AlgorithmService;
import net.onelitefeather.skygridng.core.service.RegionGenerationService;
import net.onelitefeather.skygridng.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.skygridng.core.solver.WeightedRandomCollapse;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Black-box test for the Paper chunk generator. MockBukkit provides the Material registry; the
 * WorldInfo and ChunkData are Mockito mocks so we can capture exactly which blocks get placed.
 *
 * With the SkyGrid algorithm (spacing 4) only grid columns and grid heights may receive a block,
 * which is what this proves.
 */
class HexagonalChunkGeneratorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private HexagonalChunkGenerator skyGridGenerator() {
        RuleState state = new RuleState(Fixtures.config("skygrid"), 4);
        RegionGenerationService region = new RegionGenerationService(
                state, new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
        AlgorithmService algorithms = new AlgorithmService(state, region);
        return new HexagonalChunkGenerator(algorithms, region);
    }

    @Test
    @DisplayName("the generation flags mark every phase as fully custom")
    void generationFlags() {
        HexagonalChunkGenerator generator = skyGridGenerator();
        assertThat(generator.isParallelCapable()).isTrue();
        assertThat(generator.shouldGenerateNoise()).isFalse();
        assertThat(generator.shouldGenerateSurface()).isFalse();
        assertThat(generator.shouldGenerateCaves()).isFalse();
        assertThat(generator.shouldGenerateDecorations()).isFalse();
        assertThat(generator.shouldGenerateStructures()).isFalse();
    }

    @Test
    @DisplayName("generateSurface only writes blocks on grid columns and grid heights")
    void writesOnlyOnTheGrid() {
        HexagonalChunkGenerator generator = skyGridGenerator();

        WorldInfo world = mock(WorldInfo.class);
        when(world.getSeed()).thenReturn(Fixtures.SEED);
        ChunkData data = mock(ChunkData.class);
        when(data.getMinHeight()).thenReturn(0);
        when(data.getMaxHeight()).thenReturn(64);

        generator.generateSurface(world, new Random(0), 0, 0, data);

        ArgumentCaptor<Integer> x = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> y = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> z = ArgumentCaptor.forClass(Integer.class);
        // 4 grid columns in x times 4 in z times 16 grid heights (y = 0..60 step 4) = 256 blocks.
        verify(data, times(256)).setBlock(x.capture(), y.capture(), z.capture(), any(Material.class));

        assertThat(x.getAllValues()).allMatch(v -> v == 0 || v == 4 || v == 8 || v == 12);
        assertThat(z.getAllValues()).allMatch(v -> v == 0 || v == 4 || v == 8 || v == 12);
        assertThat(y.getAllValues()).allMatch(v -> v % 4 == 0);
    }

    @Test
    @DisplayName("the SkyGrid algorithm asks for no decoration populators")
    void noPopulatorsForSkyGrid() {
        HexagonalChunkGenerator generator = skyGridGenerator();
        assertThat(generator.getDefaultPopulators(mock(org.bukkit.World.class))).isEmpty();
    }
}
