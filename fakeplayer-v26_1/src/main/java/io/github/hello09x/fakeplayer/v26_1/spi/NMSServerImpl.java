package io.github.hello09x.fakeplayer.v26_1.spi;

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
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        return new NMSServerPlayerImpl(handle.getBukkitEntity());
    }

    @Override
    public void removePlayer(@NotNull Player player, @NotNull Object reason) {
        var handle = ((CraftPlayer) player).getHandle();
        var playerList = ((CraftServer) Bukkit.getServer()).getHandle();
        try {
            findRemoveMethod(playerList.getClass(), reason).invoke(playerList, handle, reason);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            handle.disconnect();
            handle.discard();
        }
    }

    private static @NotNull Method findRemoveMethod(@NotNull Class<?> type, @NotNull Object reason) throws NoSuchMethodException {
        var current = type;
        while (current != null) {
            for (var method : current.getDeclaredMethods()) {
                if (!method.getName().equals("remove")) {
                    continue;
                }
                var parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 2) {
                    continue;
                }
                if (!parameterTypes[0].isAssignableFrom(ServerPlayer.class)) {
                    continue;
                }
                if (!parameterTypes[1].isInstance(reason)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException("remove(ServerPlayer, " + reason.getClass().getName() + ")");
    }
}
