package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps initial spawn chunks loaded while pre-login, profile IO and native joining are pending. */
public final class ChunkLeases {
    private record Key(UUID world, int x, int z) {}
    private final Map<Key, Integer> references = new java.util.concurrent.ConcurrentHashMap<>();
    private final Plugin plugin;
    private final PlatformScheduler scheduler;

    public ChunkLeases(Plugin plugin, PlatformScheduler scheduler) { this.plugin = plugin; this.scheduler = scheduler; }

    public CompletableFuture<Lease> acquire(Location location) {
        var target = location.clone();
        var world = java.util.Objects.requireNonNull(target.getWorld(), "Spawn world");
        var key = new Key(world.getUID(), target.getBlockX() >> 4, target.getBlockZ() >> 4);
        return world.getChunkAtAsync(target).thenCompose(ignored -> scheduler.callAt(target, () -> {
            references.compute(key, (ignoredKey, count) -> {
                if (count == null) world.addPluginChunkTicket(key.x(), key.z(), plugin);
                return count == null ? 1 : count + 1;
            });
            return new Lease(key, target);
        }));
    }

    public final class Lease {
        private final Key key;
        private final Location location;
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(Key key, Location location) { this.key = key; this.location = location; }

        public CompletableFuture<Void> release() {
            if (!released.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
            return scheduler.at(location, () -> {
                references.computeIfPresent(key, (ignoredKey, count) -> {
                    if (count > 1) return count - 1;
                    location.getWorld().removePluginChunkTicket(key.x(), key.z(), plugin);
                    return null;
                });
            });
        }
    }
}
