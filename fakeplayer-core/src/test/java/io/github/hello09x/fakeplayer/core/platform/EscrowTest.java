package io.github.hello09x.fakeplayer.core.platform;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EscrowTest {
    @Test void closeBeforeCommitRefundsInputsAndNeverTouchesTarget() {
        var transfer = new Escrow<>("refund", "receipt");
        assertEquals("refund", transfer.settle());
        assertFalse(transfer.commit(() -> { fail("Target must not be touched"); return true; }));
        assertNull(transfer.settle());
    }
    @Test void successfulCommitReturnsReceiptExactlyOnce() {
        var transfer = new Escrow<>("refund", "receipt");
        assertTrue(transfer.commit(() -> true)); assertEquals("receipt", transfer.settle()); assertNull(transfer.settle());
    }
    @Test void failedCommitRefundsInputs() {
        var transfer = new Escrow<>("refund", "receipt");
        assertFalse(transfer.commit(() -> false)); assertEquals("refund", transfer.settle());
    }
    @Test void throwingCommitStillSettles() {
        var transfer = new Escrow<>("refund", "receipt");
        assertThrows(IllegalStateException.class, () -> transfer.commit(() -> { throw new IllegalStateException(); }));
        assertEquals("refund", transfer.settle());
    }
    @Test void concurrentCloseAndCommitConserveBothSides() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 1000; i++) {
                var target = new AtomicInteger(10); var returned = new AtomicInteger();
                var transfer = new Escrow<>(2, 5); var start = new CountDownLatch(1);
                var commit = pool.submit(() -> { await(start); transfer.commit(() -> { target.addAndGet(-3); return true; }); });
                var close = pool.submit(() -> { await(start); var result = transfer.settle(); if (result != null) returned.addAndGet(result); });
                start.countDown(); commit.get(5, TimeUnit.SECONDS); close.get(5, TimeUnit.SECONDS);
                var remaining = transfer.settle(); if (remaining != null) returned.addAndGet(remaining);
                assertEquals(12, target.get() + returned.get());
            }
        } finally { pool.shutdownNow(); }
    }
    @Test void failedReceiptAcceptanceCanBeRetriedWithoutRefundingCommit() {
        var transfer = new Escrow<>("refund", "receipt");
        assertTrue(transfer.commit(() -> true));
        assertThrows(IllegalStateException.class, () -> transfer.settleTo(value -> { throw new IllegalStateException("closed"); }));
        var accepted = new java.util.ArrayList<String>();
        transfer.settleTo(accepted::add);
        transfer.settleTo(accepted::add);
        assertEquals(java.util.List.of("receipt"), accepted);
    }

    @Test void failedRefundAcceptanceStillCancelsPendingCommit() {
        var transfer = new Escrow<>("refund", "receipt");
        assertThrows(IllegalStateException.class, () -> transfer.settleTo(value -> { throw new IllegalStateException(); }));
        assertFalse(transfer.commit(() -> { fail("Settling cancels pending debit"); return true; }));
        var accepted = new java.util.ArrayList<String>();
        transfer.settleTo(accepted::add);
        assertEquals(java.util.List.of("refund"), accepted);
    }

    private static void await(CountDownLatch start) {
        try { start.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
