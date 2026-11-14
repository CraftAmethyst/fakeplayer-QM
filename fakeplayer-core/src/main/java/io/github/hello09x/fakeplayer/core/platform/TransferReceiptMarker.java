package io.github.hello09x.fakeplayer.core.platform;

import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Player-data markers make mailbox acknowledgements recoverable after a process crash. */
public final class TransferReceiptMarker {
    private TransferReceiptMarker() {}

    public static boolean isApplied(Player player, UUID receiptId) {
        return player.getPersistentDataContainer().has(key(receiptId), PersistentDataType.BYTE);
    }

    public static void markApplied(Player player, UUID receiptId) {
        player.getPersistentDataContainer().set(key(receiptId), PersistentDataType.BYTE, (byte) 1);
    }

    public static void clear(Player player, UUID receiptId) {
        player.getPersistentDataContainer().remove(key(receiptId));
    }

    private static NamespacedKey key(UUID receiptId) {
        return new NamespacedKey(Main.getInstance(), "transfer-" + receiptId);
    }
}
