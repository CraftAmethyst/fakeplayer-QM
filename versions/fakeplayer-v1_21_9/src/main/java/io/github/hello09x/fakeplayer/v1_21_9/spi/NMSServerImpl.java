package io.github.hello09x.fakeplayer.v1_21_9.spi;

import com.mojang.authlib.GameProfile;
import io.github.hello09x.devtools.core.utils.WorldUtils;
import io.github.hello09x.fakeplayer.api.spi.NMSServer;
import io.github.hello09x.fakeplayer.api.spi.NMSServerPlayer;
import lombok.Getter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_21_R6.CraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class NMSServerImpl implements NMSServer {


    @Getter
    private final MinecraftServer handle;

    public NMSServerImpl(@NotNull Server server) {
        this.handle = ((CraftServer) server).getServer();
    }

    @Override
    public @NotNull NMSServerPlayer newPlayer(@NotNull UUID uuid, @NotNull String name) {
        var handle = new ServerPlayer(
                new NMSServerImpl(Bukkit.getServer()).getHandle(),
                new NMSServerLevelImpl(WorldUtils.getMainWorld()).getHandle(),
                new GameProfile(uuid, name),
                ClientInformation.createDefault()
        );
        // Fake players never send the client-loaded packet, so mark them loaded explicitly.
        // Otherwise ServerPlayer#isInvulnerableTo keeps them invulnerable after spawning.
        handle.setClientLoaded(true);
        return new NMSServerPlayerImpl(handle.getBukkitEntity());
    }

    @Override
    public @NotNull NMSServerPlayer newPlayer(@NotNull UUID uuid, @NotNull String name, @NotNull org.bukkit.Location initialLocation) {
        var handle = new ServerPlayer(
                new NMSServerImpl(Bukkit.getServer()).getHandle(),
                new NMSServerLevelImpl(initialLocation.getWorld()).getHandle(),
                new GameProfile(uuid, name),
                ClientInformation.createDefault()
        );
        // Fake players never send the client-loaded packet, so mark them loaded explicitly.
        // Otherwise ServerPlayer#isInvulnerableTo keeps them invulnerable after spawning.
        handle.setClientLoaded(true);
        handle.absSnapTo(initialLocation.getX(), initialLocation.getY(), initialLocation.getZ(), initialLocation.getYaw(), initialLocation.getPitch());
        return new NMSServerPlayerImpl(handle.getBukkitEntity());
    }
}
