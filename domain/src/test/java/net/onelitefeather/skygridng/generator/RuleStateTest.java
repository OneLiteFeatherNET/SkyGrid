package net.onelitefeather.skygridng.generator;

import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * White-box tests for the shared state holder: Memento (undo) and Observer (listeners).
 */
class RuleStateTest {

    private final GeneratorConfig sky = Fixtures.config("skygrid");
    private final GeneratorConfig other = Fixtures.config("other");
    private final GeneratorConfig noise = Fixtures.config("noise_skygrid");

    @Test
    @DisplayName("apply swaps the current state and notifies listeners")
    void applyNotifies() {
        RuleState state = new RuleState(sky, 4);
        AtomicInteger fired = new AtomicInteger();
        state.addChangeListener(fired::incrementAndGet);

        state.apply(other);

        assertThat(state.current().algorithm()).isEqualTo("other");
        assertThat(fired).hasValue(1);
    }

    @Test
    @DisplayName("undo restores the previous state, notifies, and reports success")
    void undoRestores() {
        RuleState state = new RuleState(sky, 4);
        AtomicInteger fired = new AtomicInteger();
        state.addChangeListener(fired::incrementAndGet);

        state.apply(other);
        boolean undone = state.undo();

        assertThat(undone).isTrue();
        assertThat(state.current().algorithm()).isEqualTo("skygrid");
        assertThat(fired).hasValue(2); // apply + undo
    }

    @Test
    @DisplayName("undo with no history returns false and does not change state")
    void undoEmpty() {
        RuleState state = new RuleState(sky, 4);
        assertThat(state.undo()).isFalse();
        assertThat(state.current().algorithm()).isEqualTo("skygrid");
    }

    @Test
    @DisplayName("the undo history is bounded by maxUndo")
    void historyIsBounded() {
        RuleState state = new RuleState(sky, 2); // keep at most 2 mementos
        state.apply(other);
        state.apply(noise);
        state.apply(sky);
        // Three applies, but only two mementos retained.
        assertThat(state.undo()).isTrue();
        assertThat(state.undo()).isTrue();
        assertThat(state.undo()).isFalse();
    }

    @Test
    @DisplayName("replace clears the undo history")
    void replaceClearsHistory() {
        RuleState state = new RuleState(sky, 4);
        state.apply(other);
        state.replace(noise);

        assertThat(state.current().algorithm()).isEqualTo("noise_skygrid");
        assertThat(state.undo()).isFalse(); // history was cleared by replace
    }
}
