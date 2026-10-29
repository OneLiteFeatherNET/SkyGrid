package net.onelitefeather.skygridng.challenge;

import net.onelitefeather.skygridng.generator.RuleState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared, thread safe holder of the current challenge catalog.
 *
 * Simpler than {@link RuleState} on purpose: a challenge is a single idempotent boolean toggle, so
 * there is no real recovery need beyond calling the opposite verb again. No Memento/undo stack, just
 * the current snapshot (Observer for change listeners).
 *
 * Reading ({@link #current()}) is lock free (AtomicReference).
 */
public final class ChallengeState {

    private final AtomicReference<ChallengeCatalog> current;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public ChallengeState(ChallengeCatalog initial) {
        this.current = new AtomicReference<>(initial);
    }

    public ChallengeCatalog current() {
        return current.get();
    }

    /** Replaces the catalog and notifies listeners. */
    public void replace(ChallengeCatalog next) {
        current.set(next);
        fireChanged();
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
