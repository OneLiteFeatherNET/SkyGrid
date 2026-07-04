package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
import net.onelitefeather.skygridng.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.skygridng.core.solver.WeightedRandomCollapse;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the terrain algorithm catalog: the registry selects by configuration, rebuilds
 * the active algorithm when the state changes (Observer), and is the single source of truth for which
 * ids are known.
 */
class DefaultTerrainAlgorithmsTest {

    private DefaultTerrainAlgorithms algorithms(RuleState state) {
        RegionGenerationService region = new RegionGenerationService(
                state, new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
        return new DefaultTerrainAlgorithms(state, Fixtures.defaultAlgorithmFactories(), region);
    }

    @Test
    @DisplayName("the registry selects the algorithm named by the configuration")
    void selectsByConfig() {
        RuleState state = new RuleState(Fixtures.config("skygrid"), 4);
        TerrainAlgorithms catalog = algorithms(state);
        assertThat(catalog.current().id()).isEqualTo("skygrid");
    }

    @Test
    @DisplayName("changing the state rebuilds the active algorithm")
    void rebuildsOnStateChange() {
        RuleState state = new RuleState(Fixtures.config("wfc"), 4);
        TerrainAlgorithms catalog = algorithms(state);
        assertThat(catalog.current().id()).isEqualTo("wfc");

        state.apply(Fixtures.config("skygrid"));
        assertThat(catalog.current().id()).isEqualTo("skygrid");

        state.apply(Fixtures.config("noise_skygrid"));
        assertThat(catalog.current().id()).isEqualTo("noise_skygrid");
    }

    @Test
    @DisplayName("isKnown and ids reflect exactly the three shipped algorithms")
    void knownAlgorithms() {
        TerrainAlgorithms catalog = algorithms(new RuleState(Fixtures.config("skygrid"), 4));
        assertThat(catalog.isKnown("wfc")).isTrue();
        assertThat(catalog.isKnown("skygrid")).isTrue();
        assertThat(catalog.isKnown("noise_skygrid")).isTrue();
        assertThat(catalog.isKnown("nonsense")).isFalse();
        assertThat(catalog.ids()).containsExactlyInAnyOrder("skygrid", "noise_skygrid", "wfc");
    }
}
