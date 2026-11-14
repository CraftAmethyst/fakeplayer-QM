package io.github.hello09x.fakeplayer.core.manager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Counts pending joins as well as online players; one lock protects all constraints. */
public final class SpawnReservations {
    public record Entry(UUID uuid, String name, String creator, String address) {}
    public record Limits(int server, int creator, boolean detectAddress) {}
    public enum Failure { NAME, UUID, SERVER_LIMIT, CREATOR_LIMIT, ADDRESS_LIMIT }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();

    public synchronized Failure reserve(Entry entry, Limits limits) {
        var name = entry.name().toLowerCase(Locale.ROOT);
        if (names.containsKey(name)) return Failure.NAME;
        if (entries.containsKey(entry.uuid())) return Failure.UUID;
        if (limits != null) {
            if (entries.size() >= limits.server()) return Failure.SERVER_LIMIT;
            if (entries.values().stream().filter(e -> e.creator().equals(entry.creator())).count() >= limits.creator())
                return Failure.CREATOR_LIMIT;
            if (limits.detectAddress() && entries.values().stream().filter(e -> e.address().equals(entry.address())).count() >= limits.creator())
                return Failure.ADDRESS_LIMIT;
        }
        entries.put(entry.uuid(), entry);
        names.put(name, entry.uuid());
        return null;
    }

    public synchronized boolean release(Entry entry) {
        if (entries.get(entry.uuid()) != entry) return false;
        entries.remove(entry.uuid());
        names.remove(entry.name().toLowerCase(Locale.ROOT), entry.uuid());
        return true;
    }

    public synchronized boolean release(UUID uuid) {
        var entry = entries.get(uuid);
        return entry != null && release(entry);
    }

    public synchronized Entry get(UUID uuid) { return entries.get(uuid); }

    public synchronized Entry getByName(String name) {
        return entries.get(names.get(name.toLowerCase(Locale.ROOT)));
    }

    public synchronized int size() { return entries.size(); }
}
