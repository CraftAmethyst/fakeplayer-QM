package io.github.hello09x.fakeplayer.api.spi;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface NMSNetwork {

    /** Platform placement may need already decoded player data, without performing disk IO. */
    default NMSServerGamePacketListener placeNewPlayer(Server server, Player player, Object savedData) {
        return placeNewPlayer(server, player);
    }

    /** Release a virtual transport after removal or a failed join. */
    default void close() {}

    /** Close only the transport. Folia's existing region loop must finish native removal. */
    default void requestDisconnect() { close(); }

    /**
     * 绑定一个虚拟的游戏连接
     *
     * @param server 服务器
     * @param player 假人玩家
     */
    @NotNull NMSServerGamePacketListener placeNewPlayer(@NotNull Server server, @NotNull Player player);

    /**
     * 获取服务侧游戏数据包监听器
     * <p>在获取之前需要先执行了 {@link #placeNewPlayer(Server, Player)} 才会初始化值</p>
     */
    @NotNull
    NMSServerGamePacketListener getServerGamePacketListener() throws IllegalStateException;

}
