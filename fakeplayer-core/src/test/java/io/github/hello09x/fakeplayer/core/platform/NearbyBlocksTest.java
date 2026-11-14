package io.github.hello09x.fakeplayer.core.platform;

import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NearbyBlocksTest {
    @Test void delayedBlockOperationKeepsTicketUntilCompletion() {
        try (var main = mockStatic(Main.class)) {
            var scheduler = scheduler(); var leases = mock(ChunkLeases.class); var lease = mock(ChunkLeases.Lease.class);
            main.when(Main::getScheduler).thenReturn(scheduler); main.when(Main::getChunkLeases).thenReturn(leases);
            when(leases.acquire(any())).thenReturn(CompletableFuture.completedFuture(lease));
            when(lease.release()).thenReturn(CompletableFuture.completedFuture(null));
            var operation = new CompletableFuture<Boolean>();
            var result = NearbyBlocks.useAsync(player(), 0, () -> operation);
            assertFalse(result.isDone()); verify(lease, never()).release();
            operation.complete(true);
            assertTrue(result.join()); verify(lease).release();
        }
    }

    @Test void synchronousAcquireFailureStillReleasesEarlierAndLaterLeases() {
        try (var main = mockStatic(Main.class)) {
            var scheduler = scheduler(); var leases = mock(ChunkLeases.class); var lease = mock(ChunkLeases.Lease.class);
            main.when(Main::getScheduler).thenReturn(scheduler); main.when(Main::getChunkLeases).thenReturn(leases);
            when(leases.acquire(any())).thenReturn(CompletableFuture.completedFuture(lease))
                    .thenThrow(new IllegalStateException("load failed"))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("later load failed")));
            when(lease.release()).thenReturn(CompletableFuture.completedFuture(null));
            assertThrows(CompletionException.class, () -> NearbyBlocks.use(player(), 16, () -> true).join());
            verify(lease).release();
        }
    }

    private Player player() {
        var player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 8, 64, 8));
        return player;
    }

    private PlatformScheduler scheduler() {
        var scheduler = mock(PlatformScheduler.class);
        when(scheduler.isFolia()).thenReturn(true); when(scheduler.owns(any(Location.class))).thenReturn(true);
        when(scheduler.callEntity(any(), any())).thenAnswer(call -> {
            try { return CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(1)).get()); }
            catch (Throwable error) { return CompletableFuture.failedFuture(error); }
        });
        return scheduler;
    }
}
