package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.entity.Fakeplayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Singleton
public class FakeplayerList {

    private final Map<String, Fakeplayer> playersByName = new ConcurrentHashMap<>();
    private final Map<UUID, Fakeplayer> playersByUUID = new ConcurrentHashMap<>();
    private final Map<String, List<Fakeplayer>> playersByCreator = new HashMap<>();

    /**
     * 添加一个假人到假人清单
     *
     * @param player 假人
     */
    public synchronized void add(@NotNull Fakeplayer player) {
        if (playersByName.containsKey(player.getName().toLowerCase(Locale.ROOT)) || playersByUUID.containsKey(player.getUUID())) {
            throw new IllegalStateException("Fake player name or UUID already reserved: " + player.getName());
        }
        this.playersByName.put(player.getName().toLowerCase(Locale.ROOT), player);
        this.playersByUUID.put(player.getUUID(), player);
        this.playersByCreator.computeIfAbsent(player.getCreatorName(), key -> new LinkedList<>()).add(player);
    }

    /**
     * 通过假人的名称获取假人
     *
     * @param name 名称
     * @return 假人
     */
    public synchronized @Nullable Fakeplayer getByName(@NotNull String name) {
        return this.playersByName.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * 通过 UUID 获取假人
     *
     * @param uuid UUID
     * @return 假人
     */
    public synchronized @Nullable Fakeplayer getByUUID(@NotNull UUID uuid) {
        return this.playersByUUID.get(uuid);
    }

    /**
     * 获取创建者创建的所有假人
     *
     * @param creator 创建者
     * @return 假人
     */
    public synchronized @NotNull @Unmodifiable List<Fakeplayer> getByCreator(@NotNull String creator) {
        return List.copyOf(this.playersByCreator.getOrDefault(creator, List.of()));
    }

    /**
     * 移除一个假人
     *
     * @param player 假人
     */
    public synchronized void remove(@NotNull Fakeplayer player) {
        this.playersByName.remove(player.getName().toLowerCase(Locale.ROOT), player);
        this.playersByUUID.remove(player.getUUID(), player);
        var players = this.playersByCreator.get(player.getCreatorName());
        if (players != null) {
            players.remove(player);
            if (players.isEmpty()) this.playersByCreator.remove(player.getCreatorName());
        }
    }

    /**
     * 通过 UUID 移除假人
     *
     * @param uuid UUID
     * @return 被移除的假人
     */
    public synchronized @Nullable Fakeplayer removeByUUID(@NotNull UUID uuid) {
        var player = getByUUID(uuid);
        if (player == null) {
            return null;
        }
        this.remove(player);
        return player;
    }

    /**
     * 获取创建的数量
     *
     * @param creator 玩家
     * @return 数量
     */
    public synchronized int countByCreator(@NotNull String creator) {
        return Optional
                .ofNullable(this.playersByCreator.get(creator))
                .map(List::size)
                .orElse(0);
    }

    /**
     * 获取所有假人
     *
     * @return 假人
     */
    public synchronized @NotNull @Unmodifiable List<Fakeplayer> getAll() {
        return List.copyOf(this.playersByUUID.values());
    }

    /**
     * 检测假人是否在线, 如果不在线了则移除并返回 {@code null}
     *
     * @param player 假人
     * @return 假人
     */
    private @Nullable Fakeplayer checkOnline(@NotNull Fakeplayer player) {
        if (!player.isOnline()) {
            this.remove(player);
            return null;
        }

        return player;
    }

    public @NotNull Stream<Fakeplayer> stream() {
        return getAll().stream();
    }

    public synchronized int getSize() {
        return this.playersByUUID.size();
    }

}
