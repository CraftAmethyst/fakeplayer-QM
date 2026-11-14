package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkLeasesTest {
    @Test void overlappingSpawnsShareATicketUntilTheLastLeaseLeaves() {
        var world = mock(World.class); var plugin = mock(Plugin.class); var scheduler = scheduler();
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getChunkAtAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        var leases = new ChunkLeases(plugin, scheduler);
        var a = leases.acquire(new Location(world, 32, 64, -17)).join();
        var b = leases.acquire(new Location(world, 33, 64, -18)).join();
        verify(world, times(1)).addPluginChunkTicket(2, -2, plugin);
        a.release().join(); verify(world, never()).removePluginChunkTicket(anyInt(), anyInt(), any());
        b.release().join(); verify(world, times(1)).removePluginChunkTicket(2, -2, plugin);
        b.release().join(); verify(world, times(1)).removePluginChunkTicket(2, -2, plugin);
    }

    @Test void failedChunkLoadDoesNotRegisterATicket() {
        var world = mock(World.class); var plugin = mock(Plugin.class); var scheduler = scheduler();
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getChunkAtAsync(any(Location.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("load failed")));
        assertThrows(java.util.concurrent.CompletionException.class, () -> new ChunkLeases(plugin, scheduler).acquire(new Location(world, 0, 0, 0)).join());
        verify(world, never()).addPluginChunkTicket(anyInt(), anyInt(), any());
    }

    @Test void completedLeaseCanBeReacquiredAfterItsFinalRelease() {
        var world = mock(World.class); var plugin = mock(Plugin.class); var scheduler = scheduler();
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getChunkAtAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        var leases = new ChunkLeases(plugin, scheduler);
        leases.acquire(new Location(world, 0, 64, 0)).join().release().join();
        leases.acquire(new Location(world, 0, 64, 0)).join().release().join();
        verify(world, times(2)).addPluginChunkTicket(0, 0, plugin);
        verify(world, times(2)).removePluginChunkTicket(0, 0, plugin);
    }

    private PlatformScheduler scheduler() {
        var scheduler = mock(PlatformScheduler.class);
        when(scheduler.callAt(any(), any())).thenAnswer(invocation -> CompletableFuture.completedFuture(((Supplier<?>)invocation.getArgument(1)).get()));
        when(scheduler.at(any(), any())).thenAnswer(invocation -> {
            ((Runnable)invocation.getArgument(1)).run(); return CompletableFuture.completedFuture(null);
        });
        return scheduler;
    }
}
