package io.github.hello09x.fakeplayer.v1_21_4.spi;

import io.github.hello09x.fakeplayer.api.spi.NMSNetwork;
import io.github.hello09x.fakeplayer.api.spi.NMSServerGamePacketListener;
import io.github.hello09x.fakeplayer.v1_21_4.network.FakeConnection;
import io.github.hello09x.fakeplayer.v1_21_4.network.FakeServerGamePacketListenerImpl;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_21_R3.CraftServer;
import org.bukkit.craftbukkit.v1_21_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;

public class NMSNetworkImpl implements NMSNetwork {

    @NotNull
    private final FakeConnection connection;

    private NMSServerGamePacketListener serverGamePacketListener;
    private Player joinedPlayer;
    private Object joinedPlayerList;

    public NMSNetworkImpl(
            @NotNull InetAddress address
    ) {
        this.connection = new FakeConnection(address);
    }

    @Override
    public void requestDisconnect() {
        this.connection.disconnect(net.minecraft.network.chat.Component.literal("Fakeplayer plugin disabled"));
    }

    @Override
    public void close() {
        try {
            this.connection.disconnect(net.minecraft.network.chat.Component.literal("Fakeplayer transport closed"));
        } finally {
            if (joinedPlayer != null) io.github.hello09x.fakeplayer.core.platform.FoliaLifecycleHooks.unregisterConnection(
                    joinedPlayerList, connection, joinedPlayer.getName(), joinedPlayer.getUniqueId());
        }
    }

    @NotNull
    @Override
    public NMSServerGamePacketListener placeNewPlayer(
            @NotNull Server server,
            @NotNull Player player
    ) {
        return placeNewPlayer(server, player, null);
    }

    @Override
    public @NotNull NMSServerGamePacketListener placeNewPlayer(Server server, Player player, Object savedData) {
        var handle = ((CraftPlayer) player).getHandle();
        var cookie = CommonListenerCookie.createInitial(((CraftPlayer) player).getProfile(), false);

        this.joinedPlayer = player;
        this.joinedPlayerList = ((CraftServer) server).getHandle();
        io.github.hello09x.fakeplayer.core.platform.FoliaLifecycleHooks.registerConnection(joinedPlayerList, connection, player);
        if (io.github.hello09x.fakeplayer.core.Main.getScheduler().isFolia()) {
            io.github.hello09x.fakeplayer.core.platform.FoliaLifecycleHooks.placeNewPlayer(joinedPlayerList, connection, handle, cookie, player, savedData);
        } else {
            ((CraftServer) server).getHandle().placeNewPlayer(this.connection, handle, cookie);
        }

        var listener = new FakeServerGamePacketListenerImpl(
                ((CraftServer) server).getServer(),
                this.connection,
                handle,
                cookie
        );
        this.serverGamePacketListener = listener;
        handle.connection = listener;
        if (io.github.hello09x.fakeplayer.core.Main.getScheduler().isFolia()) {
            // Disconnect bookkeeping must use the same listener as region cleanup.
            this.connection.setupInboundProtocol(net.minecraft.network.protocol.game.GameProtocols.SERVERBOUND_TEMPLATE.bind(
                    net.minecraft.network.RegistryFriendlyByteBuf.decorator(((CraftServer) server).getServer().registryAccess())), listener);
        }
        return listener;
    }

    @NotNull
    @Override
    public NMSServerGamePacketListener getServerGamePacketListener() throws IllegalStateException {
        if (this.serverGamePacketListener == null) {
            throw new IllegalStateException("not initialized");
        }
        return this.serverGamePacketListener;
    }

}
