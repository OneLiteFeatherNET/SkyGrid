package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.core.port.in.TerrainAlgorithmProvider;
import net.onelitefeather.skygridng.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.skygridng.core.solver.WeightedRandomCollapse;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the algorithm provider: the factory selects by configuration, and the
 * provider rebuilds the active algorithm when the state changes (Observer).
 */
class AlgorithmServiceTest {

    private AlgorithmService provider(RuleState state) {
        RegionGenerationService region = new RegionGenerationService(
                state, new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
        return new AlgorithmService(state, region);
    }

    @Test
    @DisplayName("the factory selects the algorithm named by the configuration")
    void factorySelectsByConfig() {
        RuleState state = new RuleState(Fixtures.config("skygrid"), 4);
        TerrainAlgorithmProvider provider = provider(state);
        assertThat(provider.current().id()).isEqualTo("skygrid");
    }

    @Test
    @DisplayName("changing the state rebuilds the active algorithm")
    void rebuildsOnStateChange() {
        RuleState state = new RuleState(Fixtures.config("wfc"), 4);
        TerrainAlgorithmProvider provider = provider(state);
        assertThat(provider.current().id()).isEqualTo("wfc");

        state.apply(Fixtures.config("skygrid"));
        assertThat(provider.current().id()).isEqualTo("skygrid");

        state.apply(Fixtures.config("noise_skygrid"));
        assertThat(provider.current().id()).isEqualTo("noise_skygrid");
    }

    @Test
    @DisplayName("isKnown recognises exactly the three shipped algorithms")
    void isKnown() {
        assertThat(AlgorithmService.isKnown("wfc")).isTrue();
        assertThat(AlgorithmService.isKnown("skygrid")).isTrue();
        assertThat(AlgorithmService.isKnown("noise_skygrid")).isTrue();
        assertThat(AlgorithmService.isKnown("nonsense")).isFalse();
    }
}
