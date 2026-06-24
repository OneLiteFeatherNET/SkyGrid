package net.onelitefeather.skygridng.core.service;

import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import net.onelitefeather.skygridng.ports.out.RuleRepository;
import net.onelitefeather.skygridng.core.state.RuleState;
import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the admin use case. A simple in memory repository stands in for the JSON
 * adapter, keeping the test on the port contract.
 */
class RuleAdminServiceTest {

    /** Captures the last saved config so we can assert persistence. */
    private static final class InMemoryRepository implements RuleRepository {
        private GeneratorConfig stored = Fixtures.config("skygrid");

        @Override
        public GeneratorConfig load() {
            return stored;
        }

        @Override
        public void save(GeneratorConfig config) {
            stored = config;
        }
    }

    private InMemoryRepository repository;
    private RuleAdminUseCase admin;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        RuleState state = new RuleState(repository.load(), 8);
        admin = new RuleAdminService(state, repository);
    }

    @Test
    @DisplayName("algorithm reads and switches, rejecting unknown ids")
    void algorithmSwitching() {
        assertThat(admin.algorithm()).isEqualTo("skygrid");
        assertThat(admin.setAlgorithm("wfc")).isTrue();
        assertThat(admin.algorithm()).isEqualTo("wfc");
        assertThat(admin.setAlgorithm("bogus")).isFalse();
    }

    @Test
    @DisplayName("tile lookups return known tiles and report unknown ones")
    void tileLookups() {
        assertThat(admin.tileIds()).containsExactlyInAnyOrder("water", "sand", "grass", "cactus");
        assertThat(admin.tile("grass")).isPresent();
        assertThat(admin.tile("missing")).isEmpty();
    }

    @Test
    @DisplayName("setWeight succeeds for a known tile and fails for an unknown one")
    void setWeight() {
        assertThat(admin.setWeight("grass", 42.0)).isTrue();
        assertThat(admin.tile("grass")).get().extracting(t -> t.weight()).isEqualTo(42.0);
        assertThat(admin.setWeight("missing", 1.0)).isFalse();
    }

    @Test
    @DisplayName("allow, disallow and skyweight report success")
    void adjacencyAndSkyWeight() {
        assertThat(admin.disallow("water", "sand")).isTrue();
        assertThat(admin.allow("water", "grass")).isTrue();
        assertThat(admin.setSkyGridWeight("STONE", 9.0)).isTrue();   // existing material
        assertThat(admin.setSkyGridWeight("DIAMOND_BLOCK", 9.0)).isTrue(); // new material
    }

    @Test
    @DisplayName("save persists to the repository and undo rolls back the last change")
    void saveAndUndo() {
        admin.setAlgorithm("wfc");
        admin.save();
        assertThat(repository.load().algorithm()).isEqualTo("wfc");

        assertThat(admin.undo()).isTrue();
        assertThat(admin.algorithm()).isEqualTo("skygrid");
    }
}
