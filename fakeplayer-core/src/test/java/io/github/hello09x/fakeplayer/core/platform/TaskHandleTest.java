package io.github.hello09x.fakeplayer.core.platform;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TaskHandleTest {
    @Test void cancelBeforeBindCancelsBackendAndCompletesExactlyOnce() {
        var cancelled = new AtomicInteger(); var finished = new AtomicInteger(); var backend = new AtomicInteger();
        var handle = new TaskHandle(cancelled::incrementAndGet, finished::incrementAndGet);
        handle.cancel(); handle.bind(backend::incrementAndGet); handle.cancel(); handle.finish();
        assertEquals(1, cancelled.get()); assertEquals(1, finished.get()); assertEquals(1, backend.get());
        assertTrue(handle.isCancelled()); assertFalse(handle.isActive());
    }

    @Test void completedTaskDoesNotRunRetirementCallback() {
        var retired = new AtomicInteger(); var finished = new AtomicInteger();
        var handle = new TaskHandle(retired::incrementAndGet, finished::incrementAndGet);
        handle.finish(); handle.cancel(); handle.finish();
        assertEquals(0, retired.get()); assertEquals(1, finished.get());
    }

    @Test void concurrentSubmissionAndCancellationNeverLeaksBackend() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 1000; i++) {
                var backend = new AtomicInteger(); var cancelled = new AtomicInteger(); var finished = new AtomicInteger();
                var handle = new TaskHandle(cancelled::incrementAndGet, finished::incrementAndGet);
                var start = new CountDownLatch(1);
                var binding = pool.submit(() -> { await(start); handle.bind(backend::incrementAndGet); });
                var cancelling = pool.submit(() -> { await(start); handle.cancel(); });
                start.countDown(); binding.get(5, TimeUnit.SECONDS); cancelling.get(5, TimeUnit.SECONDS);
                assertEquals(1, backend.get()); assertEquals(1, cancelled.get()); assertEquals(1, finished.get());
            }
        } finally { pool.shutdownNow(); }
    }

    @Test void callbackFailureStillRemovesTaskFromRegistry() {
        var finished = new AtomicInteger();
        var handle = new TaskHandle(() -> { throw new IllegalStateException("retired callback"); }, finished::incrementAndGet);
        assertThrows(IllegalStateException.class, handle::cancel);
        assertEquals(1, finished.get());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
