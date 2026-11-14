package io.github.hello09x.fakeplayer.core.platform;

import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferReceiptMarkerTest {
    private MockedStatic<Main> mainStatic;

    @AfterEach void tearDown() {
        if (mainStatic != null) mainStatic.close();
    }

    @Test void receiptMarkerSurvivesUntilAcknowledgementCleanup() {
        var plugin = mock(Main.class);
        when(plugin.getName()).thenReturn("fakeplayer");
        mainStatic = mockStatic(Main.class);
        mainStatic.when(Main::getInstance).thenReturn(plugin);

        var player = mock(Player.class);
        var data = mock(PersistentDataContainer.class);
        var keys = new HashSet<NamespacedKey>();
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(data.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE)))
                .thenAnswer(call -> keys.contains(call.getArgument(0)));
        doAnswer(call -> { keys.add(call.getArgument(0)); return null; })
                .when(data).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), any(Byte.class));
        doAnswer(call -> { keys.remove(call.getArgument(0)); return null; })
                .when(data).remove(any(NamespacedKey.class));

        var receipt = UUID.randomUUID();
        assertFalse(TransferReceiptMarker.isApplied(player, receipt));
        TransferReceiptMarker.markApplied(player, receipt);
        assertTrue(TransferReceiptMarker.isApplied(player, receipt));
        TransferReceiptMarker.clear(player, receipt);
        assertFalse(TransferReceiptMarker.isApplied(player, receipt));
    }
}
