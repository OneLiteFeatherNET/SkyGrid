package net.onelitefeather.skygridng.core.state;

import net.onelitefeather.skygridng.api.model.GeneratorConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared, thread safe holder of the current generator configuration.
 *
 * Combines two patterns:
 *  - Memento: every {@link GeneratorConfig} is immutable; a change pushes the old state onto the
 *    undo stack.
 *  - Observer: registered listeners are notified on every change (for example to invalidate the
 *    region cache or rebuild the active algorithm).
 *
 * Reading ({@link #current()}) is lock free (AtomicReference) and therefore safe for parallel
 * chunk generation. Mutating operations are synchronized.
 */
public final class RuleState {

    private final AtomicReference<GeneratorConfig> current;
    private final Deque<GeneratorConfig> undoStack = new ArrayDeque<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final int maxUndo;

    public RuleState(GeneratorConfig initial, int maxUndo) {
        this.current = new AtomicReference<>(initial);
        this.maxUndo = Math.max(1, maxUndo);
    }

    public GeneratorConfig current() {
        return current.get();
    }

    /** Applies a new configuration and stores the old one as a Memento. */
    public synchronized void apply(GeneratorConfig next) {
        undoStack.push(current.getAndSet(next));
        while (undoStack.size() > maxUndo) {
            undoStack.removeLast();
        }
        fireChanged();
    }

    /** Replaces the configuration without an undo entry (for example after a reload). */
    public synchronized void replace(GeneratorConfig next) {
        current.set(next);
        undoStack.clear();
        fireChanged();
    }

    public synchronized boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        current.set(undoStack.pop());
        fireChanged();
        return true;
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
