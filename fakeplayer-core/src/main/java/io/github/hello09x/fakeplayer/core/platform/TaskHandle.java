package io.github.hello09x.fakeplayer.core.platform;

import java.util.concurrent.atomic.AtomicReference;

/** Cancellation that also works before the underlying scheduler returns its task. */
public final class TaskHandle {
    private enum State { ACTIVE, CANCELLED, FINISHED }

    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
    private final AtomicReference<Runnable> cancellation = new AtomicReference<>();
    private static final Runnable CANCELLED = () -> {};
    private final Runnable onCancel;
    private final Runnable onFinish;

    TaskHandle(Runnable onCancel, Runnable onFinish) {
        this.onCancel = onCancel;
        this.onFinish = onFinish;
    }

    void bind(Runnable cancel) {
        if (cancellation.compareAndSet(null, cancel)) return;
        if (cancellation.get() == CANCELLED) {
            cancel.run();
        } else {
            throw new IllegalStateException("Task already bound");
        }
    }

    public void cancel() {
        if (state.compareAndSet(State.ACTIVE, State.CANCELLED)) {
            try {
                var cancel = cancellation.getAndSet(CANCELLED);
                if (cancel != null && cancel != CANCELLED) cancel.run();
            } finally {
                try { onCancel.run(); } finally { onFinish.run(); }
            }
        }
    }

    void finish() {
        if (state.compareAndSet(State.ACTIVE, State.FINISHED)) onFinish.run();
    }

    boolean isActive() {
        return state.get() == State.ACTIVE;
    }

    public boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }
}
