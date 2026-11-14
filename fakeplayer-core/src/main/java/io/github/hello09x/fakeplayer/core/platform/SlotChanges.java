package io.github.hello09x.fakeplayer.core.platform;

import java.util.*;
import java.util.function.*;

/** Compare only affected slots and preserve unrelated owner-side changes. Call on one owner. */
public final class SlotChanges<T> {
    private record Change<T>(int slot, T before, T after) {}
    private final List<Change<T>> changes;
    private final BiPredicate<T, T> same;

    public SlotChanges(List<T> before, List<T> after, BiPredicate<T, T> same) {
        if (before.size() != after.size()) throw new IllegalArgumentException("Storage sizes differ");
        this.same = same;
        var changes = new ArrayList<Change<T>>();
        for (int slot = 0; slot < before.size(); slot++) {
            if (!same.test(before.get(slot), after.get(slot))) changes.add(new Change<>(slot, before.get(slot), after.get(slot)));
        }
        this.changes = List.copyOf(changes);
    }

    public boolean apply(IntFunction<T> read, BiConsumer<Integer, T> write) {
        for (var change : changes) if (!same.test(read.apply(change.slot()), change.before())) return false;
        var written = new ArrayList<Change<T>>();
        try {
            for (var change : changes) {
                // Include the current slot in rollback even when the backend throws after writing it.
                written.add(change); write.accept(change.slot(), change.after());
            }
            return true;
        } catch (RuntimeException error) {
            Collections.reverse(written);
            for (var change : written) {
                try { write.accept(change.slot(), change.before()); }
                catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            }
            throw error;
        }
    }
}
