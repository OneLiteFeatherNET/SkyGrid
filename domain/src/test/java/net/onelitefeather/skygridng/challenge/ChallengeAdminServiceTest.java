package net.onelitefeather.skygridng.challenge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the admin use case, against a fake in memory {@link ChallengeRepository}
 * (mirrors {@code RuleAdminServiceTest}'s in memory repository approach for its own out port,
 * adapted here since {@link ChallengeAdminService} saves immediately instead of deferring to a
 * separate save step).
 */
class ChallengeAdminServiceTest {

    private static final String KNOWN_ID = "no_crafting_table";

    private ChallengeState state;
    private FakeChallengeRepository repository;
    private ChallengeAdminUseCase admin;

    @BeforeEach
    void setUp() {
        ChallengeCatalog initial = new ChallengeCatalog(Map.of(
                KNOWN_ID, new ChallengeRule(KNOWN_ID, "no tables", false)));
        state = new ChallengeState(initial);
        repository = new FakeChallengeRepository(initial);
        admin = new ChallengeAdminService(state, repository);
    }

    @Test
    @DisplayName("activate on a known id flips state to active and saves the updated catalog")
    void activateKnownId() {
        boolean result = admin.activate(KNOWN_ID);

        assertThat(result).isTrue();
        assertThat(state.current().forId(KNOWN_ID)).get().extracting(ChallengeRule::active).isEqualTo(true);
        assertThat(repository.saved).isEqualTo(state.current());
    }

    @Test
    @DisplayName("deactivate on a known active id flips state to inactive and saves the updated catalog")
    void deactivateKnownId() {
        admin.activate(KNOWN_ID);
        repository.saved = null; // reset so this assertion is about the deactivate call only

        boolean result = admin.deactivate(KNOWN_ID);

        assertThat(result).isTrue();
        assertThat(state.current().forId(KNOWN_ID)).get().extracting(ChallengeRule::active).isEqualTo(false);
        assertThat(repository.saved).isEqualTo(state.current());
    }

    @Test
    @DisplayName("activate on an unknown id returns false, leaves state untouched and never saves")
    void activateUnknownId() {
        ChallengeCatalog before = state.current();

        boolean result = admin.activate("unknown");

        assertThat(result).isFalse();
        assertThat(state.current()).isEqualTo(before);
        assertThat(repository.saved).isNull();
    }

    @Test
    @DisplayName("deactivate on an unknown id returns false, leaves state untouched and never saves")
    void deactivateUnknownId() {
        ChallengeCatalog before = state.current();

        boolean result = admin.deactivate("unknown");

        assertThat(result).isFalse();
        assertThat(state.current()).isEqualTo(before);
        assertThat(repository.saved).isNull();
    }

    @Test
    @DisplayName("isActive reflects state before and after a toggle")
    void isActiveReflectsState() {
        assertThat(admin.isActive(KNOWN_ID)).isFalse();

        admin.activate(KNOWN_ID);

        assertThat(admin.isActive(KNOWN_ID)).isTrue();
    }

    @Test
    @DisplayName("isActive on an unknown id is false")
    void isActiveUnknownId() {
        assertThat(admin.isActive("unknown")).isFalse();
    }

    @Test
    @DisplayName("list returns all challenges")
    void listReturnsAll() {
        assertThat(admin.list()).extracting(ChallengeRule::id).containsExactly(KNOWN_ID);
    }

    /** Records what was saved, so a test can assert both "was saved" and "was not saved". */
    private static final class FakeChallengeRepository implements ChallengeRepository {
        private ChallengeCatalog stored;
        private ChallengeCatalog saved;

        FakeChallengeRepository(ChallengeCatalog initial) {
            this.stored = initial;
        }

        @Override
        public ChallengeCatalog load() {
            return stored;
        }

        @Override
        public void save(ChallengeCatalog catalog) {
            this.stored = catalog;
            this.saved = catalog;
        }
    }
}
