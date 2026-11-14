package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.devtools.command.exception.CommandException;
import io.github.hello09x.devtools.core.utils.Exceptions;
import io.github.hello09x.devtools.core.utils.MetadataUtils;
import io.github.hello09x.fakeplayer.api.spi.ActionSetting;
import io.github.hello09x.fakeplayer.api.spi.ActionType;
import io.github.hello09x.fakeplayer.api.spi.NMSBridge;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.constant.MetadataKeys;
import io.github.hello09x.fakeplayer.core.entity.Fakeplayer;
import io.github.hello09x.fakeplayer.core.entity.FakeplayerTicker;
import io.github.hello09x.fakeplayer.core.entity.SpawnOption;
import io.github.hello09x.fakeplayer.core.manager.feature.FakeplayerFeatureManager;
import io.github.hello09x.fakeplayer.core.manager.naming.NameManager;
import io.github.hello09x.fakeplayer.core.manager.naming.SequenceName;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import io.github.hello09x.fakeplayer.core.util.AddressUtils;
import io.github.hello09x.fakeplayer.core.util.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import io.github.hello09x.fakeplayer.core.platform.TaskHandle;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;

@Singleton
public class FakeplayerManager {

    public final static String REMOVAL_REASON_PREFIX = "[fakeplayer] ";

    private final static Logger log = Main.getInstance().getLogger();

    private final NameManager nameManager;
    private final FakeplayerList playerList;
    private final FakeplayerFeatureManager featureManager;
    private final NMSBridge nms;
    private final FakeplayerConfig config;
    private final TaskHandle lagMonitor;
    private final io.github.hello09x.fakeplayer.core.platform.ChunkLeases spawnChunks;
    private final java.util.Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();
    private final SpawnReservations reservations = new SpawnReservations();
    private final Map<UUID, CompletableFuture<Boolean>> removals = new ConcurrentHashMap<>();

    @Inject
    public FakeplayerManager(NameManager nameManager, FakeplayerList playerList, FakeplayerFeatureManager featureManager, NMSBridge nms, FakeplayerConfig config) {
        this.nameManager = nameManager;
        this.playerList = playerList;
        this.featureManager = featureManager;
        this.nms = nms;
        this.config = config;
        this.spawnChunks = Main.getChunkLeases();

        this.lagMonitor = Main.getScheduler().asyncTimer(() -> {
                    if (config.getKaleTps() <= 0) return;
                    if (Main.getScheduler().isFolia()) {
                        int threshold = config.snapshot().kaleTps();
                        var checks = getAll().stream().map(player -> Main.getScheduler().callEntity(player,
                                () -> Bukkit.getTPS()[1] < threshold
                                        ? removeAsync(player.getName(), text("low region tps"))
                                        : CompletableFuture.completedFuture(false))
                                .thenCompose(result -> result).exceptionally(error -> false)).toList();
                        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenRun(() -> {
                            if (checks.stream().noneMatch(check -> check.getNow(false))) return;
                            var message = translatable("fakeplayer.manager.remove-all-on-low-tps", GRAY, ITALIC);
                            for (var recipient : Bukkit.getOnlinePlayers()) Main.getScheduler().sender(recipient, () -> recipient.sendMessage(message));
                            Main.getScheduler().sender(Bukkit.getConsoleSender(), () -> Bukkit.getConsoleSender().sendMessage(message));
                        });
                    } else {
                        Main.getScheduler().runGlobal(() -> {
                            if (Bukkit.getServer().getTPS()[1] >= config.getKaleTps()) return;
                            if (this.removeAll("low tps") > 0) {
                                Bukkit.broadcast(translatable("fakeplayer.manager.remove-all-on-low-tps", GRAY, ITALIC));
                            }
                        });
                    }
                }, 0, 60, TimeUnit.SECONDS
        );
    }

    /**
     * 创建一个假人
     *
     * @param creator 创建者
     * @param spawnAt 生成地点
     */
    public @NotNull CompletableFuture<Player> spawnAsync(
            @NotNull CommandSender creator,
            @Nullable String name,
            @NotNull Location spawnAt,
            long lifespan
    ) {
        var location = spawnAt.clone();
        return Main.getScheduler().callSender(creator, () -> this.spawnAsync(creator, creator.getName(), creator instanceof Player p ? p.getUniqueId() : null, name, location, lifespan, null, 0, true)).thenCompose(result -> result);
    }

    public @NotNull CompletableFuture<Player> restoreAsync(
            @NotNull String creatorName,
            @Nullable UUID creatorUuid,
            @NotNull UUID uuid,
            @NotNull String name,
            @NotNull Location spawnAt,
            @NotNull Map<Feature, String> features,
            int heldSlot
    ) {
        return this.spawnAsync(
                Bukkit.getConsoleSender(),
                creatorName,
                creatorUuid,
                new SequenceName("persistent", 0, uuid, name),
                spawnAt,
                FakeplayerTicker.NON_REMOVE_AT,
                features,
                heldSlot,
                false
        );
    }

    private @NotNull CompletableFuture<Player> spawnAsync(
            @NotNull CommandSender creator,
            @NotNull String creatorName,
            @Nullable UUID creatorUuid,
            @Nullable String name,
            @NotNull Location spawnAt,
            long lifespan,
            @Nullable Map<Feature, String> restoredFeatures,
            int heldSlot,
            boolean checkLimit
    ) {
        var initialLocation = spawnAt.clone();
        return Main.getScheduler().async(() -> name == null ? nameManager.getRegularName(creatorName) : nameManager.getSpecifiedName(name))
                .thenCompose(sequence -> Main.getScheduler().callSender(creator, () -> this.spawnAsync(
                        creator, creatorName, creatorUuid, sequence, initialLocation, lifespan, restoredFeatures, heldSlot, checkLimit))
                        .whenComplete((result, error) -> { if (error != null) nameManager.unregister(sequence); }))
                .thenCompose(result -> result);
    }

    private @NotNull CompletableFuture<Player> spawnAsync(
            @NotNull CommandSender creator,
            @NotNull String creatorName,
            @Nullable UUID creatorUuid,
            @NotNull SequenceName sn,
            @NotNull Location spawnAt,
            long lifespan,
            @Nullable Map<Feature, String> restoredFeatures,
            int heldSlot,
            boolean checkLimit
    ) {
        var initialLocation = spawnAt.clone();
        var address = AddressUtils.getAddress(creator);
        var reservation = new SpawnReservations.Entry(sn.uuid(), sn.name(), creatorName, address);
        var settings = config.snapshot();
        var limits = checkLimit && !creator.isOp()
                ? new SpawnReservations.Limits(settings.serverLimit(), settings.playerLimit(), settings.detectIp())
                : null;
        var failure = reservations.reserve(reservation, limits);
        if (failure != null) {
            nameManager.unregister(sn);
            var key = switch (failure) {
                case SERVER_LIMIT -> "fakeplayer.command.spawn.error.server-limit";
                case CREATOR_LIMIT -> "fakeplayer.command.spawn.error.player-limit";
                case ADDRESS_LIMIT -> "fakeplayer.command.spawn.error.ip-limit";
                case NAME, UUID -> "fakeplayer.spawn.error.name.online";
            };
            return CompletableFuture.failedFuture(new CommandException(translatable(key, text(sn.name()))));
        }

        var created = new AtomicReference<Fakeplayer>();
        var chunkLease = new AtomicReference<io.github.hello09x.fakeplayer.core.platform.ChunkLeases.Lease>();
        CompletableFuture<Player> spawning = featureManager.getFeaturesAsync(creator).thenApply(savedFeatures -> {
            var features = savedFeatures.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().asString()));
            if (restoredFeatures != null) features.putAll(restoredFeatures);
            return new SpawnOption(initialLocation, Math.max(0, Math.min(8, heldSlot)),
                    Boolean.parseBoolean(features.get(Feature.invulnerable)),
                    Boolean.parseBoolean(features.get(Feature.collidable)),
                    Boolean.parseBoolean(features.get(Feature.look_at_entity)),
                    Boolean.parseBoolean(features.get(Feature.pickup_items)),
                    Boolean.parseBoolean(features.get(Feature.skin)),
                    Boolean.parseBoolean(features.get(Feature.replenish)),
                    Boolean.parseBoolean(features.get(Feature.autofish)),
                    Boolean.parseBoolean(features.get(Feature.wolverine)),
                    Boolean.parseBoolean(features.get(Feature.autosleep)));
        }).thenCompose(option -> {
            CompletableFuture<?> loaded = Main.getScheduler().isFolia()
                    ? spawnChunks.acquire(initialLocation).thenAccept(chunkLease::set)
                    : CompletableFuture.completedFuture(null);
            return loaded.thenCompose(ignored -> Main.getScheduler().callAt(initialLocation, () -> {
                var fp = new Fakeplayer(creator, creatorName, creatorUuid, address, sn, lifespan, initialLocation);
                created.set(fp);
                this.playerList.add(fp);
                log.info("UUID of fake player %s is %s".formatted(sn.name(), sn.uuid()));
                return fp;
            })).thenCompose(fp -> dispatchCommandsEarlyAsync(fp, config.getPreSpawnCommands())
                    .thenCompose(ignored -> Main.getScheduler().callAt(initialLocation, () -> fp.spawnAsync(option)))
                    .thenCompose(future -> future)
                    .thenApply(ignored -> fp.getPlayer()));
        });

        // A player ticket takes over once online. Every failure also releases the temporary lease.
        spawning = spawning.whenComplete((player, error) -> {
            var lease = chunkLease.get();
            if (lease != null) lease.release().exceptionally(releaseError -> {
                log.log(java.util.logging.Level.SEVERE, "Could not release initial spawn chunk lease", releaseError); return null;
            });
        });
        return spawning.handle((player, error) -> {
            if (error == null) return CompletableFuture.completedFuture(player);
            var fp = created.get();
            CompletableFuture<?> rollback;
            if (fp == null) {
                if (reservations.release(reservation)) nameManager.unregister(sn);
                rollback = CompletableFuture.completedFuture(null);
            } else if (Bukkit.getPlayer(fp.getUUID()) == fp.getPlayer()) {
                // Keep identity and capacity reserved until native removal has actually finished.
                rollback = removeAsync(fp.getName(), text("spawn failed")).thenAccept(removed -> {
                    if (!removed) throw new IllegalStateException("Spawn rollback was cancelled; identity remains reserved");
                });
            } else {
                rollback = Main.getScheduler().at(initialLocation, fp::close).thenCompose(ignored -> {
                    playerList.remove(fp);
                    return awaitDeparture(fp, reservation, null);
                });
            }
            return rollback.handle((ignored, rollbackError) -> {
                if (rollbackError != null) error.addSuppressed(rollbackError);
                return CompletableFuture.<Player>failedFuture(error);
            }).thenCompose(future -> future);
        }).thenCompose(future -> future);
    }

    /**
     * 获取一个假人
     *
     * @param creator 创建者
     * @param name    假人名称
     * @return 假人
     */
    public @Nullable Player get(@NotNull CommandSender creator, @NotNull String name) {
        return Optional
                .ofNullable(this.playerList.getByName(name))
                .filter(p -> p.isCreatedBy(creator))
                .map(Fakeplayer::getPlayer)
                .orElse(null);
    }

    /**
     * 根据名称获取假人
     *
     * @param name 名称
     * @return 假人
     */
    public @Nullable Player get(@NotNull String name) {
        return Optional
                .ofNullable(this.playerList.getByName(name))
                .map(Fakeplayer::getPlayer)
                .orElse(null);
    }

    /**
     * 获取一个假人的创建者, 如果这个玩家不是假人, 则为 {@code null}
     *
     * @param target 假人
     * @return 假人的创建者
     */
    public @Nullable String getCreatorName(@NotNull Player target) {
        return Optional
                .ofNullable(this.playerList.getByUUID(target.getUniqueId()))
                .map(Fakeplayer::getCreatorName)
                .filter(name -> !name.isBlank())
                .orElse(null);
    }

    public @Nullable UUID getCreatorUuid(@NotNull Player target) {
        return Optional
                .ofNullable(this.playerList.getByUUID(target.getUniqueId()))
                .map(Fakeplayer::getCreatorUuid)
                .orElse(null);
    }

    /**
     * 获取假人的创建者
     *
     * @param target 假人
     * @return 创建者
     */
    public @Nullable CommandSender getCreator(@NotNull Player target) {
        var fakeplayer = this.playerList.getByUUID(target.getUniqueId());
        if (fakeplayer == null) {
            return null;
        }

        if (fakeplayer.getCreatorUuid() != null) {
            var onlineCreator = Bukkit.getPlayer(fakeplayer.getCreatorUuid());
            if (onlineCreator != null) {
                return onlineCreator;
            }
        }
        return fakeplayer.getCreator();
    }

    /**
     * 根据名称删除假人
     *
     * @param name   名称
     * @param reason 原因
     * @return 是否删除成功
     */
    public boolean remove(@NotNull String name, @Nullable String reason) {
        return this.remove(name, reason == null ? null : text(reason));
    }

    /**
     * 根据名称删除假人
     *
     * @param name   名称
     * @param reason 原因
     * @return 是否移除成功
     */
    public boolean remove(@NotNull String name, @Nullable Component reason) {
        if (Main.getScheduler().isFolia()) {
            if (get(name) == null) return false;
            removeAsync(name, reason);
            return true;
        }
        var target = this.get(name);
        if (target == null) {
            return false;
        }

        this.nms.fromServer(Bukkit.getServer()).removePlayer(target, textOfChildren(
                text("[fakeplayer] "),
                reason == null ? text("removed") : reason
        ));
        return true;
    }

    /**
     * 移除所有假人
     *
     * @return 移除的假人数量
     */
    public CompletableFuture<Boolean> removeAsync(@NotNull String name, @Nullable Component reason) {
        var fp = playerList.getByName(name);
        if (fp == null) {
            // Quit listeners release the plugin index before native departure completes.
            // Join a pending removal while its identity remains reserved.
            var ticket = reservations.getByName(name);
            var pending = ticket == null ? null : removals.get(ticket.uuid());
            return pending == null ? CompletableFuture.completedFuture(false) : pending;
        }
        var future = new CompletableFuture<Boolean>();
        var existing = removals.putIfAbsent(fp.getUUID(), future);
        if (existing != null) return existing;
        Main.getScheduler().runEntity(fp.getPlayer(), () -> {
            if (!fp.isOnline()) {
                cleanup(fp.getPlayer());
                return;
            }
            var message = textOfChildren(text(REMOVAL_REASON_PREFIX), reason == null ? text("removed") : reason);
            if (Main.getScheduler().isFolia()) {
                fp.getPlayer().kick(message);
            } else {
                // Paper virtual connections do not participate in the network disconnect loop.
                // Preserve native removal while providing cancellation to asynchronous callers.
                var event = new org.bukkit.event.player.PlayerKickEvent(fp.getPlayer(), message,
                        net.kyori.adventure.text.Component.empty());
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) removalCancelled(fp.getPlayer());
                else nms.fromServer(Bukkit.getServer()).removePlayer(fp.getPlayer(), event.reason());
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                removals.remove(fp.getUUID(), future);
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    public CompletableFuture<Integer> removeAllAsync(@Nullable String reason) {
        var futures = playerList.getAll().stream().map(fp -> removeAsync(fp.getName(), reason == null ? null : text(reason))).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> (int) futures.stream().filter(future -> future.getNow(false)).count());
    }

    public int removeAll(@Nullable String reason) {
        var targets = getAll();
        if (Main.getScheduler().isFolia()) {
            removeAllAsync(reason);
            return targets.size();
        }
        for (var target : targets) {
            this.nms.fromServer(Bukkit.getServer()).removePlayer(
                    target,
                    text(REMOVAL_REASON_PREFIX + (reason == null ? "removed" : reason))
            );
        }
        return targets.size();
    }

    /**
     * @return 获取所有假人
     */
    public @NotNull List<Player> getAll() {
        return this.getAll((Predicate<Player>) null);
    }

    /**
     * @param predicate 筛选条件
     * @return 经过筛选的假人
     */
    public @NotNull List<Player> getAll(@Nullable Predicate<Player> predicate) {
        var stream = this.playerList.getAll().stream().map(Fakeplayer::getPlayer);
        if (predicate != null) {
            stream = stream.filter(predicate);
        }
        return stream.toList();
    }

    /**
     * 清理假人
     *
     * @param target 假人
     */
    public void cleanup(@NotNull Player target) {
        var fakeplayer = this.playerList.getByUUID(target.getUniqueId());
        if (fakeplayer == null) {
            return;
        }
        deadPlayers.remove(target.getUniqueId());
        Throwable failure = null;
        try {
            if (config.isDropInventoryOnQuiting()) {
                this.nms.createAction(target, ActionType.DROP_INVENTORY, ActionSetting.once()).tick();
            }
            // Capture after drops and quit listeners, before releasing the identity.
            Main.getInjector().getInstance(PersistentFakeplayerManager.class).capture(target);
        } catch (Throwable error) {
            failure = error;
        } finally {
            this.playerList.removeByUUID(target.getUniqueId());
            Main.getInjector().getInstance(PersistentFakeplayerManager.class).forget(target.getUniqueId());
            try { fakeplayer.close(); } catch (Throwable error) { failure = error; }
            awaitDeparture(fakeplayer, reservations.get(fakeplayer.getUUID()), failure);
        }
    }

    /** Native disconnect still runs on the owner after Bukkit unregisters plugin listeners. */
    public void nativeDisconnected(@NotNull Player target) {
        if (Main.getInstance().isEnabled()) return;
        var fakeplayer = playerList.removeByUUID(target.getUniqueId());
        if (fakeplayer == null) return;
        deadPlayers.remove(target.getUniqueId());
        try {
            fakeplayer.close();
        } finally {
            var ticket = reservations.get(fakeplayer.getUUID());
            if (ticket != null && reservations.release(ticket)) nameManager.unregister(fakeplayer.getSequenceName());
        }
    }

    /**
     * 获取创建者创建的所有假人
     *
     * @param creator 创建者
     * @return 创建者创建的假人
     */
    public @NotNull List<Player> getAll(@NotNull CommandSender creator) {
        return this.getAll(creator, null);
    }

    /**
     * 获取筛选过的创建者创建的假人
     *
     * @param creator   创建者
     * @param predicate 筛选条件
     * @return 假人
     */
    public @NotNull List<Player> getAll(@NotNull CommandSender creator, @Nullable Predicate<Player> predicate) {
        var stream = this.playerList.getByCreator(creator.getName()).stream().map(Fakeplayer::getPlayer);
        if (predicate != null) {
            stream = stream.filter(predicate);
        }
        return stream.toList();
    }

    private CompletableFuture<Boolean> awaitDeparture(Fakeplayer fp, SpawnReservations.Entry ticket, Throwable failure) {
        var result = new CompletableFuture<Boolean>();
        var polls = new java.util.concurrent.atomic.AtomicInteger();
        var poller = Main.getScheduler().globalTimer(() -> {
            // Only consult server identity indexes; entity state belongs to its region.
            if (Bukkit.getPlayer(fp.getUUID()) == fp.getPlayer() || Bukkit.getPlayerExact(fp.getName()) == fp.getPlayer()) {
                if (polls.incrementAndGet() >= 200) {
                    result.completeExceptionally(new IllegalStateException("Native departure did not finish; identity remains reserved: " + fp.getName()));
                }
                return;
            }
            if (ticket != null && reservations.release(ticket)) nameManager.unregister(fp.getSequenceName());
            if (failure == null) result.complete(true);
            else result.completeExceptionally(failure);
        }, 1, 1, () -> result.completeExceptionally(new java.util.concurrent.CancellationException("Departure watch cancelled")));
        result.whenComplete((removed, error) -> {
            poller.cancel();
            var pending = removals.remove(fp.getUUID());
            if (pending != null) {
                if (error == null) pending.complete(removed);
                else pending.completeExceptionally(error);
            }
        });
        return result;
    }

    /** A cancelled native kick must settle the caller instead of waiting for a quit event forever. */
    public void removalCancelled(Player player) {
        var pending = removals.remove(player.getUniqueId());
        if (pending != null) pending.complete(false);
    }

    public Location getLocationSnapshot(Player player) {
        var fp = playerList.getByUUID(player.getUniqueId());
        return fp == null ? null : fp.getLocationSnapshot();
    }

    public void publishDead(Player player) {
        var fp = playerList.getByUUID(player.getUniqueId());
        if (fp != null) fp.publishLocation();
        if (player.isDead()) deadPlayers.add(player.getUniqueId());
        else deadPlayers.remove(player.getUniqueId());
    }

    public boolean isDeadSnapshot(Player player) {
        return Main.getScheduler().isFolia() ? deadPlayers.contains(player.getUniqueId()) : player.isDead();
    }

    public int getSize() {
        return this.playerList.getSize();
    }

    /**
     * 判断一名玩家是否是假人
     *
     * @param target 玩家
     * @return 是否是假人
     */
    public boolean isFake(@NotNull Player target) {
        return this.isFake(target.getUniqueId());
    }

    public boolean isFake(@NotNull UUID uuid) {
        return this.playerList.getByUUID(uuid) != null;
    }

    /**
     * 判断一名玩家不是假人
     *
     * @param target 玩家
     * @return 是否不是假人
     */
    public boolean isNotFake(@NotNull Player target) {
        return !this.isFake(target);
    }

    /**
     * 获取 IP 地址创建着多少个假人
     *
     * @param address IP 地址
     * @return 该 IP 地址创建着多少个假人
     */
    public long countByAddress(@NotNull String address) {
        return this.playerList
                .stream()
                .filter(p -> p.getCreatorIp().equals(address))
                .count();
    }

    /**
     * 获取这个玩家创建了多少个假人
     *
     * @param creator 玩家
     * @return 创建了多少个假人
     */
    public int countByCreator(@NotNull CommandSender creator) {
        return this.playerList.countByCreator(creator.getName());
    }

    /**
     * 设置玩家当前选择的假人
     *
     * @param creator 玩家
     * @param target  假人
     */
    public void setSelection(@NotNull Player creator, @Nullable Player target) {
        if (target == null) {
            creator.removeMetadata(MetadataKeys.SELECTION, Main.getInstance());
            return;
        }

        if (!this.isFake(target)) {
            return;
        }

        creator.setMetadata(MetadataKeys.SELECTION, new FixedMetadataValue(Main.getInstance(), target.getUniqueId()));
    }

    /**
     * 获取当前选中的假人
     *
     * @param creator 创建者
     * @return 选中的假人
     */
    public @Nullable Player getSelection(@NotNull CommandSender creator) {
        if (!(creator instanceof Player p)) {
            return null;
        }
        if (!p.hasMetadata(MetadataKeys.SELECTION)) {
            return null;
        }

        var uuid = MetadataUtils
                .find(Main.getInstance(), p, MetadataKeys.SELECTION, UUID.class)
                .map(MetadataValue::value)
                .map(UUID.class::cast)
                .orElse(null);

        if (uuid == null) {
            return null;
        }

        var target = Optional.ofNullable(this.playerList.getByUUID(uuid)).map(Fakeplayer::getPlayer).orElse(null);
        if (target == null) {
            this.setSelection(p, null);
        }
        return target;
    }

    /**
     * 以假人身份执行命令
     *
     * @param target   假人
     * @param commands 命令
     */
    public void issueCommands(@NotNull Player target, @NotNull List<String> commands) {
        if (!Main.getScheduler().owns(target)) {
            Main.getScheduler().runEntity(target, () -> issueCommands(target, commands));
            return;
        }
        if (commands.isEmpty()) {
            return;
        }
        if (this.isNotFake(target)) {
            return;
        }

        var p = target.getName();
        var u = target.getUniqueId().toString();
        var c = Objects.requireNonNull(this.getCreatorName(target));
        for (var cmd : Commands.formatCommands(commands, "%p", p, "%u", u, "%c", c)) {
            if (!target.performCommand(cmd)) {
                log.warning(target.getName() + " failed to execute command: " + cmd);
            } else {
                log.info(target.getName() + " issued command: " + cmd);
            }
        }
    }

    public void dispatchCommandsLater(Player player, List<String> commands, long delay) {
        var fp = playerList.getByUUID(player.getUniqueId());
        if (fp != null) Main.getScheduler().globalLater(() -> dispatchCommandsEarlyAsync(fp, commands), delay);
    }

    public void dispatchCommandsEarly(@NotNull Fakeplayer fp, @NotNull List<String> commands) {
        dispatchCommandsEarlyAsync(fp, commands);
    }

    public CompletableFuture<Void> dispatchCommandsEarlyAsync(@NotNull Fakeplayer fp, @NotNull List<String> commands) {
        if (commands.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var server = Bukkit.getServer();
        var sender = Bukkit.getConsoleSender();
        var p = fp.getName();
        var u = fp.getUUID().toString();
        var c = fp.getCreatorName();
        var formatted = Commands.formatCommands(commands, "%p", p, "%u", u, "%c", c);
        return Main.getScheduler().runGlobal(() -> {
        for (var cmd : formatted) {
            if (!server.dispatchCommand(sender, cmd)) {
                log.warning("Failed to execute command for %s: ".formatted(p) + cmd);
            } else {
                log.info("Dispatched command: " + cmd);
            }
        }
        });
    }

    /**
     * 以控制台身份对玩家执行命令
     *
     * @param player   假人
     * @param commands 命令
     */
    public void dispatchCommands(@NotNull Player player, @NotNull List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }

        var fp = playerList.getByUUID(player.getUniqueId());
        if (fp != null) dispatchCommandsEarlyAsync(fp, commands);
    }

    /**
     * 检测限制, 不满足条件则抛出异常
     *
     * @param creator 创建者
     */
    private void checkLimit(@NotNull CommandSender creator) throws CommandException {
        if (creator.isOp()) {
            return;
        }

        if (this.playerList.getSize() >= this.config.getServerLimit()) {
            throw new CommandException(translatable("fakeplayer.command.spawn.error.server-limit"));
        }

        if (this.playerList.getByCreator(creator.getName()).size() >= this.config.getPlayerLimit()) {
            throw new CommandException(translatable("fakeplayer.command.spawn.error.player-limit"));
        }

        if (this.config.isDetectIp() && this.countByAddress(AddressUtils.getAddress(creator)) >= this.config.getPlayerLimit()) {
            throw new CommandException(translatable("fakeplayer.command.spawn.error.ip-limit"));
        }
    }

    public void onDisable() {
        if (!Main.getScheduler().isFolia()) {
            var departing = playerList.getAll();
            Exceptions.suppress(Main.getInstance(), () -> this.removeAll("Plugin disabled"));
            // Bukkit unregisters quit listeners before onDisable on both platforms.
            // Paper removal is synchronous, so finish the plugin objects here.
            departing.forEach(fp -> {
                if (Bukkit.getPlayer(fp.getUUID()) != fp.getPlayer()) {
                    Exceptions.suppress(Main.getInstance(), () -> nativeDisconnected(fp.getPlayer()));
                }
            });
        } else if (!Bukkit.getServer().isStopping()) {
            // Registered transports remain in Folia's native region loop until it
            // observes the closed channel and removes the player on its owner.
            playerList.getAll().forEach(fp -> Exceptions.suppress(Main.getInstance(), fp::shutdownTransport));
        }
        removals.values().forEach(future -> future.completeExceptionally(new java.util.concurrent.CancellationException("Plugin disabled")));
        removals.clear();
        Exceptions.suppress(Main.getInstance(), this.lagMonitor::cancel);
    }

}
