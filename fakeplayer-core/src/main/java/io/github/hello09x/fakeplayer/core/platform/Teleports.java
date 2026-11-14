package io.github.hello09x.fakeplayer.core.platform;

import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

public final class Teleports {
    private Teleports() {}

    private static final java.util.Set<java.util.UUID> busy = new java.util.HashSet<>();

    private static synchronized boolean acquire(Player... players) {
        for (var player : players) if (busy.contains(player.getUniqueId())) return false;
        for (var player : players) busy.add(player.getUniqueId());
        return true;
    }

    private static synchronized void release(Player... players) {
        for (var player : players) busy.remove(player.getUniqueId());
    }

    public static CompletableFuture<Boolean> to(Player player, Location destination) {
        if (!acquire(player)) return CompletableFuture.completedFuture(false);
        return move(player, destination).whenComplete((ignored, error) -> release(player));
    }

    public static CompletableFuture<Boolean> swap(Player first, Player second) {
        if (first.getUniqueId().equals(second.getUniqueId())) return CompletableFuture.completedFuture(true);
        if (!acquire(first, second)) return CompletableFuture.completedFuture(false);
        return Main.getScheduler().callEntity(first, first::getLocation).thenCompose(a ->
                Main.getScheduler().callEntity(second, second::getLocation).thenCompose(b ->
                        move(second, a).thenCompose(moved -> {
                            if (!moved) return CompletableFuture.completedFuture(false);
                            return move(first, b).handle((success, error) -> {
                                if (error == null && Boolean.TRUE.equals(success)) return CompletableFuture.completedFuture(true);
                                return move(second, b).thenApply(restored -> {
                                    if (!restored) throw new IllegalStateException("Position swap failed and rollback was cancelled", error);
                                    if (error != null) throw new java.util.concurrent.CompletionException(error);
                                    return false;
                                });
                            }).thenCompose(result -> result);
                        }))).whenComplete((ignored, error) -> release(first, second));
    }

    private static CompletableFuture<Boolean> move(Player player, Location destination) {
        var target = destination.clone();
        return Main.getScheduler().callEntity(player, () -> {
            if (!player.isOnline()) return CompletableFuture.completedFuture(false);
            var fp = Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.FakeplayerList.class).getByUUID(player.getUniqueId());
            if (fp != null && !fp.beginMigration()) return CompletableFuture.completedFuture(false);
            var shielded = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                var actualTarget = target;
                if (Main.getScheduler().isFolia()) {
                    // Folia's native asynchronous move does not dispatch PlayerTeleportEvent.
                    // Invoke protection hooks on the source owner before any region migration.
                    var event = new PlayerTeleportEvent(player, player.getLocation().clone(), target.clone(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled() || event.getTo() == null || !player.isOnline()) {
                        if (fp != null) fp.endMigration();
                        return CompletableFuture.completedFuture(false);
                    }
                    actualTarget = event.getTo().clone();
                    // PaperMC/Folia#421: crossing dimensions invalidates the source player entity for a tick.
                    // Protect fakeplayer pearls from that false-death window; same-world moves do not need it.
                    if (fp != null && !player.getWorld().getUID().equals(actualTarget.getWorld().getUID())) {
                        shielded.set(FoliaLifecycleHooks.shieldEnderPearls(player));
                    }
                }
                var movement = Main.getScheduler().isFolia()
                        ? player.teleportAsync(actualTarget, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        : CompletableFuture.completedFuture(player.teleport(actualTarget, PlayerTeleportEvent.TeleportCause.PLUGIN));
                return movement
                        .whenComplete((success, error) -> {
                            if (fp != null) fp.endMigration();
                            if (shielded.get()) {
                                Main.getScheduler().runEntity(player, () -> FoliaLifecycleHooks.restoreEnderPearls(player))
                                        .exceptionally(restoreError -> {
                                            Main.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                                                    "Cannot restore ender pearl shield after Folia teleport", restoreError);
                                            return null;
                                        });
                            }
                        });
            } catch (RuntimeException error) {
                if (fp != null) fp.endMigration();
                if (shielded.get()) {
                    Main.getScheduler().runEntity(player, () -> FoliaLifecycleHooks.restoreEnderPearls(player));
                }
                throw error;
            }
        }).thenCompose(result -> result).thenCompose(success -> {
            if (!success) return CompletableFuture.completedFuture(false);
            return Main.getScheduler().callEntity(player, () -> {
                if (!player.isOnline()) return false;
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
                return true;
            });
        });
    }
}
