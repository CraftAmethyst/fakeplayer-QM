package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** All server work must state which entity, location, or global context owns it. */
public final class PlatformScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final ServerPlatform platform;
    private final Set<TaskHandle> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public PlatformScheduler(Plugin plugin, ServerPlatform platform) {
        this.plugin = plugin;
        this.platform = platform;
    }

    public boolean isFolia() { return platform == ServerPlatform.FOLIA; }

    public boolean owns(Entity entity) {
        return isFolia() ? Bukkit.isOwnedByCurrentRegion(entity) : Bukkit.isPrimaryThread();
    }

    public boolean owns(Location location) {
        return isFolia() ? Bukkit.isOwnedByCurrentRegion(location) : Bukkit.isPrimaryThread();
    }

    public TaskHandle entity(Entity entity, Runnable action) { return entityLater(entity, action, 1); }

    public TaskHandle entityLater(Entity entity, Runnable action, long delay) {
        return entityTask(entity, action, delay, 0, () -> {});
    }

    public TaskHandle entityTimer(Entity entity, Runnable action, long delay, long period) {
        return entityTimer(entity, action, delay, period, () -> {});
    }

    public TaskHandle entityTimer(Entity entity, Runnable action, long delay, long period, Runnable retired) {
        if (period < 1) throw new IllegalArgumentException("period must be positive");
        return entityTask(entity, action, delay, period, retired);
    }

    private TaskHandle entityTask(Entity entity, Runnable action, long delay, long period, Runnable retired) {
        requireDelay(delay);
        return schedule(handle -> {
            Runnable run = guarded(handle, action, period > 0);
            if (isFolia()) {
                var task = period > 0
                        ? entity.getScheduler().runAtFixedRate(plugin, ignored -> run.run(), handle::cancel, Math.max(1, delay), period)
                        : entity.getScheduler().runDelayed(plugin, ignored -> run.run(), handle::cancel, Math.max(1, delay));
                if (task == null) handle.cancel();
                else handle.bind(task::cancel);
            } else {
                var task = period > 0
                        ? Bukkit.getScheduler().runTaskTimer(plugin, run, delay, period)
                        : Bukkit.getScheduler().runTaskLater(plugin, run, delay);
                handle.bind(task::cancel);
            }
        }, retired);
    }

    public TaskHandle global(Runnable action) { return globalLater(action, 1); }

    public TaskHandle globalLater(Runnable action, long delay) { return globalTask(action, delay, 0, () -> {}); }

    public TaskHandle globalTimer(Runnable action, long delay, long period) {
        if (period < 1) throw new IllegalArgumentException("period must be positive");
        return globalTask(action, delay, period, () -> {});
    }

    public TaskHandle globalTimer(Runnable action, long delay, long period, Runnable cancelled) {
        if (period < 1) throw new IllegalArgumentException("period must be positive");
        return globalTask(action, delay, period, cancelled);
    }

    private TaskHandle globalTask(Runnable action, long delay, long period, Runnable cancelled) {
        requireDelay(delay);
        return schedule(handle -> {
            Runnable run = guarded(handle, action, period > 0);
            if (isFolia()) {
                var task = period > 0
                        ? Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> run.run(), Math.max(1, delay), period)
                        : Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> run.run(), Math.max(1, delay));
                handle.bind(task::cancel);
            } else {
                var task = period > 0
                        ? Bukkit.getScheduler().runTaskTimer(plugin, run, delay, period)
                        : Bukkit.getScheduler().runTaskLater(plugin, run, delay);
                handle.bind(task::cancel);
            }
        }, cancelled);
    }

    public CompletableFuture<Void> at(Location location, Runnable action) {
        var result = new CompletableFuture<Void>();
        var target = location.clone();
        schedule(handle -> {
            var run = guarded(handle, () -> complete(result, () -> { action.run(); return null; }), false);
            if (isFolia()) {
                var task = Bukkit.getRegionScheduler().run(plugin, target, ignored -> run.run());
                handle.bind(task::cancel);
            } else {
                var task = Bukkit.getScheduler().runTask(plugin, run);
                handle.bind(task::cancel);
            }
        }, () -> result.completeExceptionally(new CancellationException("Location task cancelled")));
        return result;
    }

    public <T> CompletableFuture<T> callAt(Location location, Supplier<T> action) {
        var result = new CompletableFuture<T>();
        return at(location, () -> complete(result, action)).thenCompose(ignored -> result);
    }

    public <T> CompletableFuture<T> callEntity(Entity entity, Supplier<T> action) {
        var result = new CompletableFuture<T>();
        if (!closed.get() && owns(entity)) {
            complete(result, action);
        } else {
            entityTask(entity, () -> complete(result, action), 1, 0,
                    () -> result.completeExceptionally(new CancellationException("Entity retired or task cancelled")));
        }
        return result;
    }

    public CompletableFuture<Void> runEntity(Entity entity, Runnable action) {
        return callEntity(entity, () -> { action.run(); return null; });
    }

    public <T> CompletableFuture<T> callEntityLater(Entity entity, long delay, Supplier<T> action) {
        var result = new CompletableFuture<T>();
        entityTask(entity, () -> complete(result, action), delay, 0,
                () -> result.completeExceptionally(new CancellationException("Entity retired or delayed task cancelled")));
        return result;
    }

    public CompletableFuture<Void> runGlobal(Runnable action) {
        var result = new CompletableFuture<Void>();
        if (!closed.get() && (isFolia() ? Bukkit.isGlobalTickThread() : Bukkit.isPrimaryThread())) {
            complete(result, () -> { action.run(); return null; });
        } else {
            globalTask(() -> complete(result, () -> { action.run(); return null; }), 1, 0,
                    () -> result.completeExceptionally(new CancellationException("Global task cancelled")));
        }
        return result;
    }

    public <T> CompletableFuture<T> async(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        schedule(handle -> {
            var task = Bukkit.getAsyncScheduler().runNow(plugin,
                    ignored -> guarded(handle, () -> complete(result, action), false).run());
            handle.bind(task::cancel);
        }, () -> result.completeExceptionally(new CancellationException("Async task cancelled")));
        return result;
    }

    public TaskHandle asyncTimer(Runnable action, long initialDelay, long period, TimeUnit unit) {
        if (initialDelay < 0 || period <= 0) throw new IllegalArgumentException("Invalid timer interval");
        return schedule(handle -> {
            var task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                    ignored -> guarded(handle, action, true).run(), initialDelay, period, unit);
            handle.bind(task::cancel);
        }, () -> {});
    }

    public CompletableFuture<Void> sender(CommandSender sender, Runnable action) {
        return sender instanceof Entity entity ? runEntity(entity, action) : runGlobal(action);
    }

    public <T> CompletableFuture<T> callSender(CommandSender sender, Supplier<T> action) {
        if (sender instanceof Entity entity) return callEntity(entity, action);
        var result = new CompletableFuture<T>();
        return runGlobal(() -> complete(result, action)).thenCompose(ignored -> result);
    }

    private TaskHandle schedule(Consumer<TaskHandle> submit, Runnable cancelled) {
        // An identity reference lets completion remove its own handle, including during submission.
        var reference = new java.util.concurrent.atomic.AtomicReference<TaskHandle>();
        var handle = new TaskHandle(cancelled, () -> tasks.remove(reference.get()));
        reference.set(handle);
        tasks.add(handle);
        if (closed.get()) {
            handle.cancel();
            return handle;
        }
        try {
            submit.accept(handle);
        } catch (RuntimeException exception) {
            handle.cancel();
            if (!(exception instanceof RejectedExecutionException) && plugin.isEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Cannot schedule server task", exception);
            }
        }
        return handle;
    }

    private Runnable guarded(TaskHandle handle, Runnable action, boolean repeating) {
        return () -> {
            if (!handle.isActive()) return;
            try {
                action.run();
            } catch (Throwable exception) {
                handle.cancel();
                plugin.getLogger().log(Level.SEVERE, "Scheduled fakeplayer task failed", exception);
            } finally {
                if (!repeating) handle.finish();
            }
        };
    }

    private static <T> void complete(CompletableFuture<T> result, Supplier<T> action) {
        try { result.complete(action.get()); } catch (Throwable exception) { result.completeExceptionally(exception); }
    }

    private static void requireDelay(long delay) {
        if (delay < 0) throw new IllegalArgumentException("delay must not be negative");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) tasks.forEach(task -> {
            try { task.cancel(); }
            catch (Throwable error) { plugin.getLogger().log(Level.WARNING, "Task cancellation callback failed", error); }
        });
    }
}
