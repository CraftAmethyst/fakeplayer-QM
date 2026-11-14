package io.github.hello09x.fakeplayer.core.platform;

import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Load adjacent chunks and let Folia merge their ownership before a player uses their blocks. */
public final class NearbyBlocks {
    private NearbyBlocks() {}

    public static <T> CompletableFuture<T> use(Player player, int radius, Supplier<T> action) {
        return useAsync(player, radius, () -> CompletableFuture.completedFuture(action.get()));
    }

    /** Keep leases until delayed work finishes, including failure or owner retirement. */
    public static <T> CompletableFuture<T> useAsync(Player player, int radius, Supplier<CompletableFuture<T>> action) {
        return Main.getScheduler().callEntity(player, () -> {
            if (!player.isOnline()) throw new CancellationException("Player disconnected before block access");
            var origin = player.getLocation().clone();
            if (!Main.getScheduler().isFolia()) return action.get();
            var chunks = new ArrayList<Location>();
            for (int x = (origin.getBlockX() - radius) >> 4; x <= (origin.getBlockX() + radius) >> 4; x++) {
                for (int z = (origin.getBlockZ() - radius) >> 4; z <= (origin.getBlockZ() + radius) >> 4; z++) {
                    chunks.add(new Location(origin.getWorld(), x * 16, origin.getY(), z * 16));
                }
            }
            // Spawn preparation and nearby access share Bukkit's one ticket per plugin/chunk.
            var leases = new ArrayList<CompletableFuture<ChunkLeases.Lease>>();
            for (var chunk : chunks) {
                try { leases.add(Main.getChunkLeases().acquire(chunk)); }
                catch (RuntimeException error) { leases.add(CompletableFuture.failedFuture(error)); }
            }
            var ready = CompletableFuture.allOf(leases.toArray(CompletableFuture[]::new));
            return ready.thenCompose(ignored -> awaitOwner(player, origin, chunks, action, 40))
                    .whenComplete((value, error) -> leases.forEach(lease -> {
                        if (!lease.isCompletedExceptionally() && !lease.isCancelled()) {
                            var acquired = lease.getNow(null);
                            if (acquired != null) acquired.release().exceptionally(releaseError -> {
                                Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Cannot release nearby block chunk lease", releaseError);
                                return null;
                            });
                        }
                    }));
        }).thenCompose(result -> result);
    }

    private static <T> CompletableFuture<T> awaitOwner(Player player, Location origin, List<Location> chunks,
                                                       Supplier<CompletableFuture<T>> action, int attempts) {
        return Main.getScheduler().callEntity(player, () -> {
            if (!player.isOnline() || player.isDead()) throw new CancellationException("Player unavailable during block access");
            var current = player.getLocation();
            if (!current.getWorld().equals(origin.getWorld()) || current.distanceSquared(origin) > 1) {
                throw new CancellationException("Player moved during nearby block preparation");
            }
            if (chunks.stream().allMatch(Main.getScheduler()::owns)) return action.get();
            if (attempts <= 0) return CompletableFuture.<T>failedFuture(new IllegalStateException("Adjacent chunks did not merge into the player's region"));
            var nextTick = new CompletableFuture<Void>();
            var task = Main.getScheduler().entityTimer(player, () -> nextTick.complete(null), 1, 1,
                    () -> nextTick.completeExceptionally(new CancellationException("Nearby block owner retired")));
            nextTick.whenComplete((ignored, error) -> task.cancel());
            return nextTick.thenCompose(ignored -> awaitOwner(player, origin, chunks, action, attempts - 1));
        }).thenCompose(result -> result);
    }
}
