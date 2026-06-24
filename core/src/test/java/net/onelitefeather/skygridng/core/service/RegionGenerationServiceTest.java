package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.RuleSet;
import net.onelitefeather.skygridng.ports.in.GenerateRegionUseCase;
import net.onelitefeather.skygridng.core.solver.ShannonEntropyHeuristic;
import net.onelitefeather.skygridng.core.solver.WeightedRandomCollapse;
import net.onelitefeather.skygridng.core.solver.WfcSolver;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests against the {@link GenerateRegionUseCase} port contract: deterministic tiles
 * whose neighbours respect the configured adjacency.
 */
class RegionGenerationServiceTest {

    private GenerateRegionUseCase region() {
        RuleState state = new RuleState(Fixtures.config("wfc"), 4);
        return new RegionGenerationService(
                state, new WfcSolver(new ShannonEntropyHeuristic(), new WeightedRandomCollapse()));
    }

    @Test
    @DisplayName("rules() exposes the active WFC rule set")
    void rulesExposeConfig() {
        GenerateRegionUseCase region = region();
        RuleSet rules = region.rules();
        assertThat(rules.settings().cellSize()).isEqualTo(4);
        assertThat(rules.settings().surfaceY()).isEqualTo(64);
        assertThat(rules.tileIds()).containsExactlyInAnyOrder("water", "sand", "grass", "cactus");
    }

    @Test
    @DisplayName("tileAt is deterministic for the same seed and cell")
    void tileAtIsDeterministic() {
        GenerateRegionUseCase region = region();
        assertThat(region.tileAt(Fixtures.SEED, 2, 3).id())
                .isEqualTo(region.tileAt(Fixtures.SEED, 2, 3).id());
    }

    @Test
    @DisplayName("neighbouring cells always satisfy the adjacency rules")
    void neighboursRespectAdjacency() {
        GenerateRegionUseCase region = region();
        RuleSet rules = region.rules();
        for (int cx = -8; cx <= 8; cx++) {
            for (int cz = -8; cz <= 8; cz++) {
                String here = region.tileAt(Fixtures.SEED, cx, cz).id();
                String east = region.tileAt(Fixtures.SEED, cx + 1, cz).id();
                String south = region.tileAt(Fixtures.SEED, cx, cz + 1).id();
                assertThat(compatible(rules, here, east)).as("%s next to %s", here, east).isTrue();
                assertThat(compatible(rules, here, south)).as("%s next to %s", here, south).isTrue();
            }
        }
    }

    private boolean compatible(RuleSet rules, String a, String b) {
        if (a.equals(b)) {
            return rules.settings().allowSelf();
        }
        return rules.allowed().stream()
                .anyMatch(p -> (p.a().equals(a) && p.b().equals(b)) || (p.a().equals(b) && p.b().equals(a)));
    }
}
