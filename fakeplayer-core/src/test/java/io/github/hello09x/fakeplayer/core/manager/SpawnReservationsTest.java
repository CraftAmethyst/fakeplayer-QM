package io.github.hello09x.fakeplayer.core.manager;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SpawnReservationsTest {
    private SpawnReservations.Entry entry(String name, String creator, String address) {
        return new SpawnReservations.Entry(UUID.randomUUID(), name, creator, address);
    }

    @Test void namesAreCaseInsensitiveAndUuidIsUnique() {
        var store = new SpawnReservations(); var first = entry("Alice", "creator", "address");
        assertNull(store.reserve(first, null));
        assertSame(first, store.getByName("ALICE"));
        assertEquals(SpawnReservations.Failure.NAME, store.reserve(entry("ALICE", "other", "other"), null));
        assertEquals(SpawnReservations.Failure.UUID, store.reserve(new SpawnReservations.Entry(first.uuid(), "Bob", "other", "other"), null));
        assertEquals(1, store.size());
    }

    @Test void staleReleaseCannotReleaseNewGenerationWithSameIdentity() {
        var store = new SpawnReservations(); var first = entry("Alice", "creator", "address");
        assertNull(store.reserve(first, null)); assertTrue(store.release(first));
        var replacement = new SpawnReservations.Entry(first.uuid(), first.name(), first.creator(), first.address());
        assertNull(store.reserve(replacement, null));
        assertFalse(store.release(first)); assertEquals(1, store.size());
        assertTrue(store.release(replacement)); assertEquals(0, store.size());
        assertNull(store.getByName("Alice"));
    }

    @Test void allThreeLimitsIncludePendingReservations() {
        var store = new SpawnReservations(); assertNull(store.reserve(entry("a", "one", "ip"), null));
        assertEquals(SpawnReservations.Failure.SERVER_LIMIT, store.reserve(entry("b", "two", "other"), new SpawnReservations.Limits(1, 10, false)));
        assertEquals(SpawnReservations.Failure.CREATOR_LIMIT, store.reserve(entry("b", "one", "other"), new SpawnReservations.Limits(10, 1, false)));
        assertEquals(SpawnReservations.Failure.ADDRESS_LIMIT, store.reserve(entry("b", "two", "ip"), new SpawnReservations.Limits(10, 1, true)));
        assertEquals(1, store.size());
    }

    @Test void concurrentSpawnsCannotOverbookLimits() throws Exception {
        var store = new SpawnReservations(); var pool = Executors.newFixedThreadPool(16);
        try {
            var gate = new CountDownLatch(1); var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) {
                var candidate = entry("fake" + i, "creator", "address");
                futures.add(pool.submit(() -> {
                    gate.await();
                    return store.reserve(candidate, new SpawnReservations.Limits(7, 3, true)) == null;
                }));
            }
            gate.countDown(); int accepted = 0;
            for (var result : futures) if (result.get(5, TimeUnit.SECONDS)) accepted++;
            assertEquals(3, accepted); assertEquals(3, store.size());
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentSameNameSpawnsHaveSingleWinner() throws Exception {
        var store = new SpawnReservations(); var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) futures.add(pool.submit(() -> store.reserve(entry("same", "c", "ip"), null) == null));
            int accepted = 0;
            for (var result : futures) if (result.get(5, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
        } finally { pool.shutdownNow(); }
    }
}
