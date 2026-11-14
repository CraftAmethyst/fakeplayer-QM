package io.github.hello09x.fakeplayer.v26_1.network;

import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.network.FakeChannel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.logging.Logger;

public class FakeConnection extends Connection {

    private final static Logger log = Main.getInstance().getLogger();
    private final FakeplayerManager manager = Main.getInjector().getInstance(FakeplayerManager.class);

    public FakeConnection(@NotNull InetAddress address) {
        super(PacketFlow.SERVERBOUND);
        this.channel = new FakeChannel(null, address);
        this.address = this.channel.remoteAddress();
        Connection.configureSerialization(this.channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
    }

    @Override
    public void tick() {
        // Folia registers virtual transports with its regional connection loop.
        // The entity ticker owns doTick; the clientless connection must not tick it again.
        if (!Main.getScheduler().isFolia()) super.tick();
    }

    @Override
    public boolean isConnected() {
        return this.channel != null && this.channel.isActive();
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelfuturelistener) {
        if (channelfuturelistener != null) {
            try { channelfuturelistener.operationComplete(this.channel.newSucceededFuture()); }
            catch (Exception error) { log.log(java.util.logging.Level.WARNING, "Virtual packet completion failed", error); }
        }
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelfuturelistener, boolean flag) {
        send(packet, channelfuturelistener);
    }


    @Override
    public void send(Packet<?> packet) {

    }

}
