package io.github.hello09x.fakeplayer.core.platform;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A short commit critical section races safely with disconnect and shutdown settlement. */
public final class Escrow<T> {
    private enum State { PENDING, COMMITTED, REJECTED, SETTLED }
    private State state = State.PENDING;
    private final T refund;
    private final T receipt;

    public Escrow(T refund, T receipt) {
        this.refund = Objects.requireNonNull(refund);
        this.receipt = Objects.requireNonNull(receipt);
    }

    public synchronized boolean commit(BooleanSupplier operation) {
        if (state != State.PENDING) return false;
        state = State.REJECTED;
        if (!operation.getAsBoolean()) return false;
        state = State.COMMITTED;
        return true;
    }

    /** The receiver accepts a receipt before it is forgotten; failed acceptance may be retried. */
    public synchronized void settleTo(java.util.function.Consumer<T> receiver) {
        if (state == State.SETTLED) return;
        if (state == State.PENDING) state = State.REJECTED;
        var payload = state == State.COMMITTED ? receipt : refund;
        receiver.accept(payload);
        state = State.SETTLED;
    }

    /** Returns the chosen payload exactly once; settling a pending operation cancels it. */
    public synchronized T settle() {
        if (state == State.SETTLED) return null;
        var payload = state == State.COMMITTED ? receipt : refund;
        state = State.SETTLED;
        return payload;
    }
}
