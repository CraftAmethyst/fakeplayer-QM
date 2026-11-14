package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Folia-only lifecycle entry points, kept out of the Paper NMS compile classpath. */
public final class FoliaLifecycleHooks {
    private FoliaLifecycleHooks() {}

    /** Older Folia releases split data loading from regional placement and change this signature. */
    public static void placeNewPlayer(Object playerList, Object connection, Object handle, Object cookie, Player player, Object savedData) {
        try {
            var method = java.util.Arrays.stream(playerList.getClass().getMethods())
                    .filter(candidate -> candidate.getName().equals("placeNewPlayer"))
                    .filter(candidate -> candidate.getParameterCount() == 3 || candidate.getParameterCount() == 6 || candidate.getParameterCount() == 7)
                    .findFirst().orElseThrow();
            switch (method.getParameterCount()) {
                case 3 -> method.invoke(playerList, connection, handle, cookie);
                // Attachment data is restored once on the final owner after the spawn detour.
                case 6 -> method.invoke(playerList, connection, handle, cookie, FoliaPlayerData.placementData(player, savedData), player.getName(), player.getLocation().clone());
                case 7 -> {
                    var collector = method.getParameterTypes()[3].getConstructor(org.slf4j.Logger.class)
                            .newInstance(io.github.hello09x.fakeplayer.core.Main.getInstance().getSLF4JLogger());
                    // The placement method owns and closes its ScopedCollector.
                    method.invoke(playerList, connection, handle, cookie, collector, FoliaPlayerData.placementData(player, savedData), player.getName(), player.getLocation().clone());
                }
                default -> throw new IllegalStateException("Unknown Folia join signature");
            }
            var regionServer = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            var instance = regionServer.getMethod("getInstance").invoke(null);
            var add = java.util.Arrays.stream(regionServer.getMethods()).filter(candidate -> candidate.getName().equals("addConnection")).findFirst().orElseThrow();
            add.invoke(instance, connection);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Folia player placement failed", error instanceof InvocationTargetException invocation ? invocation.getCause() : error);
        }
    }

    /** Reject occupied identities without disconnecting their real owner. */
    public static void registerConnection(Object playerList, Object connection, Player player) {
        if (!io.github.hello09x.fakeplayer.core.Main.getScheduler().isFolia()) return;
        try {
            var type = playerList.getClass();
            while (type != null && !type.getSimpleName().equals("PlayerList")) type = type.getSuperclass();
            if (type == null) throw new NoSuchFieldException("PlayerList superclass");
            var lockField = type.getDeclaredField("connectionsStateLock");
            var namesField = type.getDeclaredField("connectionByName");
            var idsField = type.getDeclaredField("connectionById");
            lockField.setAccessible(true);
            namesField.setAccessible(true);
            idsField.setAccessible(true);
            synchronized (lockField.get(playerList)) {
                var names = (java.util.Map<?, ?>) namesField.get(playerList);
                var ids = (java.util.Map<?, ?>) idsField.get(playerList);
                if (names.containsKey(player.getName().toLowerCase(java.util.Locale.ROOT)) || ids.containsKey(player.getUniqueId())) {
                    throw new IllegalStateException("Player identity already connected: " + player.getName());
                }
                var method = java.util.Arrays.stream(type.getMethods())
                        .filter(m -> m.getName().equals("pushPendingJoin")).findFirst().orElseThrow();
                if (!Boolean.TRUE.equals(method.invoke(playerList, player.getName(), player.getUniqueId(), connection))) {
                    throw new IllegalStateException("Folia rejected pending fakeplayer join");
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported Folia connection bookkeeping", error);
        }
    }

    public static void unregisterConnection(Object playerList, Object connection, String name, java.util.UUID id) {
        if (!io.github.hello09x.fakeplayer.core.Main.getScheduler().isFolia()) return;
        try {
            var method = java.util.Arrays.stream(playerList.getClass().getMethods())
                    .filter(m -> m.getName().equals("removeConnection") && m.getParameterCount() == 3).findFirst().orElseThrow();
            method.invoke(playerList, name, id, connection);
            var regionServer = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            var instance = regionServer.getMethod("getInstance").invoke(null);
            var remove = java.util.Arrays.stream(regionServer.getMethods()).filter(candidate -> candidate.getName().equals("removeConnection")).findFirst().orElseThrow();
            remove.invoke(instance, connection);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot release Folia connection bookkeeping", error);
        }
    }

    /**
     * PaperMC/Folia#421: changing dimensions marks the source player entity removed for a tick.
     * Vanilla pearls treat that as owner death and discard themselves. {@code wonGame} is the
     * native opt-out for exactly that check, so shield it only across the transfer window.
     * Shields nest: the flag is cleared only after the last overlapping transfer finishes.
     * @return true when the native flag was found and the shield is active
     */
    public static boolean shieldEnderPearls(Player player) {
        synchronized (shields) {
            try {
                var field = wonGameField(player);
                if (field == null) return false;
                var id = player.getUniqueId();
                var state = shields.get(id);
                var handle = player.getClass().getMethod("getHandle").invoke(player);
                if (state == null) {
                    state = new ShieldState();
                    state.previous = field.getBoolean(handle);
                    shields.put(id, state);
                }
                state.depth++;
                field.setBoolean(handle, true);
                return true;
            } catch (ReflectiveOperationException | RuntimeException error) {
                io.github.hello09x.fakeplayer.core.Main.getInstance().getLogger()
                        .warning("Cannot shield ender pearls during Folia dimension transfer: " + error);
                return false;
            }
        }
    }

    public static void restoreEnderPearls(Player player) {
        synchronized (shields) {
            try {
                var state = shields.get(player.getUniqueId());
                if (state == null || --state.depth > 0) return;
                shields.remove(player.getUniqueId());
                var current = org.bukkit.Bukkit.getPlayer(player.getUniqueId());
                if (current == null) current = player;
                var field = wonGameField(current);
                if (field == null) return;
                var handle = current.getClass().getMethod("getHandle").invoke(current);
                field.setBoolean(handle, state.previous);
            } catch (ReflectiveOperationException | RuntimeException error) {
                shields.remove(player.getUniqueId());
                io.github.hello09x.fakeplayer.core.Main.getInstance().getLogger()
                        .warning("Cannot restore ender pearl shield after Folia dimension transfer: " + error);
            }
        }
    }

    private static final class ShieldState {
        private int depth;
        private boolean previous;
    }

    private static final java.util.Map<java.util.UUID, ShieldState> shields = new java.util.HashMap<>();

    private static java.lang.reflect.Field wonGameField(Player player) throws ReflectiveOperationException {
        Class<?> type = player.getClass().getMethod("getHandle").invoke(player).getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField("wonGame");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    public static CompletableFuture<Void> respawn(Player player) {
        var result = new CompletableFuture<Void>();
        if (!player.isDead()) return CompletableFuture.completedFuture(null);
        var fp = io.github.hello09x.fakeplayer.core.Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.FakeplayerList.class).getByUUID(player.getUniqueId());
        if (fp != null && !fp.beginMigration()) return CompletableFuture.failedFuture(new IllegalStateException("Fakeplayer is already migrating"));
        var watcher = io.github.hello09x.fakeplayer.core.Main.getScheduler().entityTimer(player, () -> {
            if (!player.isOnline()) result.completeExceptionally(new java.util.concurrent.CancellationException("Disconnected during respawn"));
        }, 1, 1, () -> result.completeExceptionally(new java.util.concurrent.CancellationException("Retired during respawn")));
        result.whenComplete((ignored, error) -> { if (fp != null) fp.endMigration(); watcher.cancel(); });
        try {
            var handle = player.getClass().getMethod("getHandle").invoke(player);
            var method = handle.getClass().getMethod("respawn", Consumer.class, PlayerRespawnEvent.RespawnReason.class);
            method.invoke(handle, (Consumer<Object>) ignored -> result.complete(null), PlayerRespawnEvent.RespawnReason.PLUGIN);
        } catch (ReflectiveOperationException exception) {
            result.completeExceptionally(exception instanceof InvocationTargetException invocation
                    ? invocation.getCause() : exception);
        }
        return result;
    }
}
