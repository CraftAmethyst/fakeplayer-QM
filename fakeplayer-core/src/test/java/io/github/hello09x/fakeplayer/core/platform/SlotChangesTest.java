package io.github.hello09x.fakeplayer.core.platform;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SlotChangesTest {
    @Test void unrelatedPickupIsPreserved() {
        var change = new SlotChanges<>(List.of(5, 0), List.of(3, 0), Objects::equals);
        var storage = new ArrayList<>(List.of(5, 7));
        assertTrue(change.apply(storage::get, storage::set)); assertEquals(List.of(3, 7), storage);
    }
    @Test void consumedOrReplenishedSlotRejectsWholeTransaction() {
        var change = new SlotChanges<>(List.of(5, 2), List.of(3, 4), Objects::equals);
        var storage = new ArrayList<>(List.of(4, 2));
        assertFalse(change.apply(storage::get, storage::set)); assertEquals(List.of(4, 2), storage);
    }
    @Test void partialBackendFailureRestoresAppliedSlots() {
        var change = new SlotChanges<>(List.of(5, 2, 0), List.of(3, 4, 0), Objects::equals);
        var storage = new ArrayList<>(List.of(5, 2, 9));
        assertThrows(IllegalStateException.class, () -> change.apply(storage::get, (slot, value) -> {
            storage.set(slot, value); if (slot == 1 && value == 4) throw new IllegalStateException("backend failure after write");
        }));
        assertEquals(List.of(5, 2, 9), storage);
    }
    @Test void nullRepresentsEmptySlot() {
        var change = new SlotChanges<>(Arrays.asList(null, "stone"), Arrays.asList("apple", null), Objects::equals);
        var storage = new ArrayList<>(Arrays.asList(null, "stone"));
        assertTrue(change.apply(storage::get, storage::set)); assertEquals(Arrays.asList("apple", null), storage);
    }
}
