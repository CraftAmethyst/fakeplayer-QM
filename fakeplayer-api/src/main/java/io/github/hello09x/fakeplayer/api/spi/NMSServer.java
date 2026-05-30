package io.github.hello09x.fakeplayer.api.spi;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface NMSServer {

    /**
     * 创建一名新的玩家加入游戏
     *
     * @param uuid UUID
     * @param name 名称
     * @return 假人
     */
    @NotNull NMSServerPlayer newPlayer(@NotNull UUID uuid, @NotNull String name);

    default void removePlayer(@NotNull Player player, @NotNull Object reason) {
        if (reason instanceof Component component) {
            player.kick(component);
        } else {
            player.kick(Component.text(String.valueOf(reason)));
        }
    }

}
