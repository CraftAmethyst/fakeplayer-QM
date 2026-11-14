package io.github.hello09x.fakeplayer.core.manager.action;

import io.github.hello09x.fakeplayer.api.spi.*;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.platform.PlatformScheduler;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActionManagerTest {
    @Test void nativeQuitStopsActionsAfterPluginSchedulerCloses() {
        try (var main = mockStatic(Main.class)) {
            var scheduler = mock(PlatformScheduler.class);
            main.when(Main::getScheduler).thenReturn(scheduler);
            when(scheduler.runEntity(any(), any())).thenAnswer(call -> {
                ((Runnable) call.getArgument(1)).run(); return CompletableFuture.completedFuture(null);
            });
            var player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var bridge = mock(NMSBridge.class); var ticker = mock(ActionTicker.class);
            var setting = ActionSetting.continuous(); when(ticker.getSetting()).thenReturn(setting);
            when(bridge.createAction(player, ActionType.ATTACK, setting)).thenReturn(ticker);
            var manager = new ActionManager(bridge);
            manager.setAction(player, ActionType.ATTACK, setting);
            assertTrue(manager.hasActiveAction(player, ActionType.ATTACK));
            clearInvocations(scheduler);
            manager.stopOwned(player);
            verify(ticker).stop(); verifyNoInteractions(scheduler);
            assertTrue(manager.getActiveActions(player).isEmpty());
        }
    }
}
