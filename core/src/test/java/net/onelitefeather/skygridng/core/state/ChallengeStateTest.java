package net.onelitefeather.skygridng.core.state;

import net.onelitefeather.skygridng.api.model.ChallengeCatalog;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the challenge state holder: Observer (change listeners) only, deliberately no
 * Memento/undo (see {@link RuleStateTest} for the richer holder that does have one).
 */
class ChallengeStateTest {

    private final ChallengeCatalog inactive = new ChallengeCatalog(Map.of(
            "no_crafting_table", new ChallengeRule("no_crafting_table", "no tables", false)));
    private final ChallengeCatalog active = new ChallengeCatalog(Map.of(
            "no_crafting_table", new ChallengeRule("no_crafting_table", "no tables", true)));

    @Test
    @DisplayName("current returns the catalog passed at construction")
    void currentReturnsInitial() {
        ChallengeState state = new ChallengeState(inactive);

        assertThat(state.current()).isEqualTo(inactive);
    }

    @Test
    @DisplayName("replace updates what current returns")
    void replaceUpdatesCurrent() {
        ChallengeState state = new ChallengeState(inactive);

        state.replace(active);

        assertThat(state.current()).isEqualTo(active);
    }

    @Test
    @DisplayName("a registered listener fires exactly once per replace call")
    void listenerFiresOncePerReplace() {
        ChallengeState state = new ChallengeState(inactive);
        AtomicInteger fired = new AtomicInteger();
        state.addChangeListener(fired::incrementAndGet);

        state.replace(active);
        assertThat(fired).hasValue(1);

        state.replace(inactive);
        assertThat(fired).hasValue(2);
    }
}
