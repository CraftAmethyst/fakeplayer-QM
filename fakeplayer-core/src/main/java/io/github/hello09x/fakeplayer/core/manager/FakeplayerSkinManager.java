package io.github.hello09x.fakeplayer.core.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.repository.FakeplayerSkinRepository;
import io.github.hello09x.fakeplayer.core.repository.model.FakePlayerSkin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * @author tanyaofei
 * @since 2024/8/8
 **/
@Singleton
public class FakeplayerSkinManager {

    private final static Logger log = Main.getInstance().getLogger();
    private final FakeplayerSkinRepository repository;
    private final FakeplayerConfig config;
    private final Cache<UUID, PlayerProfile> profileCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    @Inject
    public FakeplayerSkinManager(FakeplayerSkinRepository repository, FakeplayerConfig config) {
        this.repository = repository;
        this.config = config;
    }

    @CanIgnoreReturnValue
    public boolean rememberSkin(@NotNull CommandSender creator, @NotNull Player to, @NotNull OfflinePlayer from) {
        if (!(creator instanceof Player p)) {
            return false;
        }

        repository.insertOrUpdate(new FakePlayerSkin(
                to.getUniqueId(),
                p.getUniqueId(),
                from.getUniqueId()
        ));
        return true;
    }

    public void useDefaultSkin(@NotNull CommandSender creator, @NotNull Player to) {
        useDefaultSkinAsync(creator, to).exceptionally(error -> {
            log.warning("Failed to apply default skin: " + error.getMessage());
            return false;
        });
    }

    public CompletableFuture<Boolean> useDefaultSkinAsync(CommandSender creator, Player to) {
        var creatorId = creator instanceof Player player ? player.getUniqueId() : null;
        return Main.getScheduler().async(() -> creatorId == null ? null
                : repository.selectByCreatorIdAndPlayerId(creatorId, to.getUniqueId())).thenCompose(skin -> {
            if (skin != null) return useOnlineSkinAsync(to, Bukkit.getOfflinePlayer(skin.targetId()));
            if (config.isDefaultOnlineSkin()) return useOnlineSkinAsync(to, Bukkit.getOfflinePlayer(to.getName()));
            if (creator instanceof Player player) return profile(player).thenCompose(copy ->
                    Main.getScheduler().callEntity(to, () -> {
                        if (!copy.hasTextures()) return false;
                        setTexture(to, copy);
                        return true;
                    }));
            return CompletableFuture.completedFuture(false);
        });
    }

    /** Synchronous SPI for callers already owning both players (Paper path). */
    @CanIgnoreReturnValue
    public boolean useSkin(@NotNull Player to, @NotNull OfflinePlayer from) {
        if (!Main.getScheduler().owns(to) || from instanceof Player player && !Main.getScheduler().owns(player)) return false;
        var profile = from.getPlayerProfile();
        if (!profile.hasTextures()) profile = profileCache.getIfPresent(from.getUniqueId());
        if (profile == null || !profile.hasTextures()) return false;
        setTexture(to, profile);
        return true;
    }

    private CompletableFuture<PlayerProfile> profile(OfflinePlayer from) {
        if (from instanceof Player player) return Main.getScheduler().callEntity(player, () -> (PlayerProfile) player.getPlayerProfile().clone());
        return Main.getScheduler().async(() -> (PlayerProfile) from.getPlayerProfile().clone());
    }

    @CanIgnoreReturnValue
    public @NotNull CompletableFuture<Boolean> useOnlineSkinAsync(@NotNull Player to, @NotNull OfflinePlayer from) {
        var id = from.getUniqueId();
        return profile(from).thenCompose(profile -> Main.getScheduler().async(() -> {
            var cached = profileCache.getIfPresent(id);
            if (!profile.hasTextures() && cached != null) return (PlayerProfile) cached.clone();
            if (!profile.hasTextures() && !profile.complete()) return null;
            if (!profile.hasTextures()) return null;
            profileCache.put(id, (PlayerProfile) profile.clone());
            return profile;
        })).thenCompose(profile -> {
            if (profile == null) return CompletableFuture.completedFuture(false);
            return Main.getScheduler().callEntity(to, () -> {
                if (!to.isOnline()) return false;
                setTexture(to, profile);
                return true;
            });
        });
    }

    public CompletableFuture<Boolean> rememberSkinAsync(CommandSender creator, Player to, OfflinePlayer from) {
        return Main.getScheduler().async(() -> rememberSkin(creator, to, from));
    }

    private void setTexture(@NotNull Player to, @NotNull PlayerProfile fromProfile) {
        var toProfile = to.getPlayerProfile();
        toProfile.setTextures(fromProfile.getTextures());
        fromProfile.getProperties().stream().filter(p -> p.getName().equals("textures")).findAny().ifPresent(toProfile::setProperty);
        to.setPlayerProfile(toProfile);
    }

    private enum ProfileCompleteResult {
        SUCCESS,
        FAILED,
        ERROR
    }


}
