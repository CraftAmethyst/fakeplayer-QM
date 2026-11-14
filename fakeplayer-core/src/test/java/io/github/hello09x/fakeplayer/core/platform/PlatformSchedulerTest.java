package io.github.hello09x.fakeplayer.core.platform;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PlatformSchedulerTest {
    private Plugin plugin() {
        var plugin = mock(Plugin.class); when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger()); return plugin;
    }

    @Test void retiredEntityCompletesPendingFutureInsteadOfHanging() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var plugin = plugin(); var entity = mock(Entity.class); var backend = mock(EntityScheduler.class);
            var retirement = new AtomicReference<Runnable>(); var task = mock(ScheduledTask.class);
            when(entity.getScheduler()).thenReturn(backend);
            when(backend.runDelayed(eq(plugin), any(), any(), anyLong())).thenAnswer(call -> { retirement.set(call.getArgument(2)); return task; });
            var scheduler = new PlatformScheduler(plugin, ServerPlatform.FOLIA);
            var result = scheduler.callEntity(entity, () -> 42);
            assertFalse(result.isDone()); retirement.get().run();
            assertThrows(CancellationException.class, result::join); verify(task).cancel(); scheduler.close();
        }
    }

    @Test void nullBackendTaskImmediatelyCancelsFuture() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var entity = mock(Entity.class); when(entity.getScheduler()).thenReturn(mock(EntityScheduler.class));
            var scheduler = new PlatformScheduler(plugin(), ServerPlatform.FOLIA);
            assertThrows(CancellationException.class, () -> scheduler.callEntity(entity, () -> 42).join()); scheduler.close();
        }
    }

    @Test void shutdownCompletesAllWaitersAndPreventsLaterExecution() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var plugin = plugin(); var entity = mock(Entity.class); var backend = mock(EntityScheduler.class);
            var task = mock(ScheduledTask.class); var callback = new AtomicReference<Consumer<ScheduledTask>>();
            when(entity.getScheduler()).thenReturn(backend);
            when(backend.runDelayed(eq(plugin), any(), any(), anyLong())).thenAnswer(call -> { callback.set(call.getArgument(1)); return task; });
            var scheduler = new PlatformScheduler(plugin, ServerPlatform.FOLIA); var executions = new AtomicInteger();
            var result = scheduler.callEntity(entity, executions::incrementAndGet);
            scheduler.close(); callback.get().accept(task);
            assertThrows(CancellationException.class, result::join); assertEquals(0, executions.get());
            assertThrows(CancellationException.class, () -> scheduler.callEntity(entity, executions::incrementAndGet).join());
            verify(task, times(1)).cancel();
        }
    }

    @Test void actionExceptionReachesCaller() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var plugin = plugin(); var entity = mock(Entity.class); var backend = mock(EntityScheduler.class);
            var callback = new AtomicReference<Consumer<ScheduledTask>>(); var task = mock(ScheduledTask.class);
            when(entity.getScheduler()).thenReturn(backend);
            when(backend.runDelayed(eq(plugin), any(), any(), anyLong())).thenAnswer(call -> { callback.set(call.getArgument(1)); return task; });
            var scheduler = new PlatformScheduler(plugin, ServerPlatform.FOLIA);
            var expected = new IllegalStateException("action failed");
            var result = scheduler.callEntity(entity, () -> { throw expected; }); callback.get().accept(task);
            assertSame(expected, assertThrows(CompletionException.class, result::join).getCause()); scheduler.close();
        }
    }
}
