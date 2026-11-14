package io.github.hello09x.fakeplayer.core.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class Skins {

    /**
     * 复制皮肤, 仅能复制在线玩家的皮肤
     *
     * @param from 皮肤来源
     * @param to   复制到
     * @return 是否复制成功
     */
    public static boolean copySkin(@NotNull OfflinePlayer from, @NotNull Player to) {
        var scheduler = io.github.hello09x.fakeplayer.core.Main.getScheduler();
        var source = from.getPlayer();
        if (scheduler.isFolia() && (!scheduler.owns(to) || source != null && !scheduler.owns(source))) {
            throw new IllegalStateException("Use copySkinFromMojang for cross-region skin copies");
        }
        var profile = from.getPlayerProfile();
        if (profile.hasTextures()) {
            copyTexture(profile, to);
            return true;
        }
        return false;
    }

    /**
     * 复制皮肤, 如果来源玩家不在线, 则通过 mojang API 下载皮肤后再复制
     *
     * @param from 皮肤来源
     * @param to   复制到
     */
    public static CompletableFuture<Boolean> copySkinFromMojang(@NotNull JavaPlugin plugin, @NotNull OfflinePlayer from, @NotNull Player to) {
        var scheduler = io.github.hello09x.fakeplayer.core.Main.getScheduler();
        var source = from.getPlayer();
        CompletableFuture<PlayerProfile> captured = source == null
                ? scheduler.async(from::getPlayerProfile)
                : scheduler.callEntity(source, () -> (PlayerProfile) source.getPlayerProfile().clone());
        return captured.thenCompose(profile -> {
            var completed = profile.hasTextures() ? CompletableFuture.completedFuture(true) : scheduler.async(profile::complete);
            return completed.thenCompose(success -> scheduler.callEntity(to, () -> {
                if (!success || !to.isOnline()) return false;
                copyTexture(profile, to);
                return true;
            }));
        });
    }

    private static void copyTexture(@NotNull PlayerProfile from, @NotNull Player to) {
        var profile = to.getPlayerProfile();
        profile.setTextures(from.getTextures());
        from.getProperties().stream().filter(p -> p.getName().equals("textures")).findAny().ifPresent(profile::setProperty);
        to.setPlayerProfile(profile);
    }


}
