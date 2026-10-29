package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.ports.in.TerrainAlgorithms;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the terrain algorithm catalog: the registry selects by configuration, rebuilds
 * the active algorithm when the state changes (Observer), and is the single source of truth for which
 * ids are known. An unrecognized algorithm id falls back to the independent SkyGrid algorithm.
 */
class DefaultTerrainAlgorithmsTest {

    private DefaultTerrainAlgorithms algorithms(RuleState state) {
        return new DefaultTerrainAlgorithms(state, Fixtures.defaultAlgorithmFactories());
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
        RuleState state = new RuleState(Fixtures.config("noise_skygrid"), 4);
        TerrainAlgorithms catalog = algorithms(state);
        assertThat(catalog.current().id()).isEqualTo("noise_skygrid");

        state.apply(Fixtures.config("skygrid"));
        assertThat(catalog.current().id()).isEqualTo("skygrid");
    }

    @Test
    @DisplayName("an unknown algorithm id falls back to the independent SkyGrid algorithm")
    void fallsBackToSkyGridForUnknownId() {
        RuleState state = new RuleState(Fixtures.config("bogus"), 4);
        TerrainAlgorithms catalog = algorithms(state);
        assertThat(catalog.current().id()).isEqualTo("skygrid");
    }

    @Test
    @DisplayName("isKnown and ids reflect exactly the two shipped algorithms")
    void knownAlgorithms() {
        TerrainAlgorithms catalog = algorithms(new RuleState(Fixtures.config("skygrid"), 4));
        assertThat(catalog.isKnown("skygrid")).isTrue();
        assertThat(catalog.isKnown("noise_skygrid")).isTrue();
        assertThat(catalog.isKnown("nonsense")).isFalse();
        assertThat(catalog.ids()).containsExactlyInAnyOrder("skygrid", "noise_skygrid");
    }
}
