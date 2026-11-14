package io.github.hello09x.fakeplayer.core.entity;

import io.github.hello09x.devtools.command.exception.CommandException;
import io.github.hello09x.devtools.core.utils.EntityUtils;
import io.github.hello09x.devtools.core.utils.WorldUtils;
import io.github.hello09x.fakeplayer.api.spi.*;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.config.PreventKicking;
import io.github.hello09x.fakeplayer.core.constant.MetadataKeys;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerAutofishManager;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerAutosleepManager;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerReplenishManager;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerSkinManager;
import io.github.hello09x.fakeplayer.core.manager.action.ActionManager;
import io.github.hello09x.fakeplayer.core.manager.naming.SequenceName;
import io.github.hello09x.fakeplayer.core.util.Attributes;
import io.github.hello09x.fakeplayer.core.util.FakeplayerFeatureUtils;
import io.github.hello09x.fakeplayer.core.util.InternalAddressGenerator;
import io.github.hello09x.fakeplayer.core.util.VersionUtils;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import lombok.Getter;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import io.github.hello09x.fakeplayer.core.platform.Teleports;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class Fakeplayer {

    public enum State { INITIALIZING, JOINING, ONLINE, MIGRATING, REMOVING, REMOVED }
    private final AtomicReference<State> state = new AtomicReference<>(State.INITIALIZING);
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

    public State getState() { return state.get(); }
    private volatile Location locationSnapshot;
    public Location getLocationSnapshot() { var location = locationSnapshot; return location == null ? null : location.clone(); }
    public void publishLocation() { locationSnapshot = player.getLocation().clone(); }
    public boolean beginMigration() { return state.get() == State.JOINING || state.compareAndSet(State.ONLINE, State.MIGRATING); }
    public void endMigration() { state.compareAndSet(State.MIGRATING, State.ONLINE); }

    private final static InternalAddressGenerator ipGen = new InternalAddressGenerator();
    private final static FakeplayerConfig config = Main.getInjector().getInstance(FakeplayerConfig.class);
    private final static NMSBridge bridge = Main.getInjector().getInstance(NMSBridge.class);
    private final static FakeplayerSkinManager skinManager = Main.getInjector().getInstance(FakeplayerSkinManager.class);
    private final static FakeplayerReplenishManager replenishManager = Main.getInjector().getInstance(FakeplayerReplenishManager.class);
    private final static FakeplayerAutofishManager autofishManager = Main.getInjector().getInstance(FakeplayerAutofishManager.class);
    private final static FakeplayerAutosleepManager autosleepManager = Main.getInjector().getInstance(FakeplayerAutosleepManager.class);
    private final static ActionManager actionManager = Main.getInjector().getInstance(ActionManager.class);


    @NotNull
    @Getter
    private final CommandSender creator;

    @NotNull
    @Getter
    private final String creatorName;

    @Nullable
    @Getter
    private final UUID creatorUuid;

    @NotNull
    @Getter
    private final NMSServerPlayer handle;

    @NotNull
    @Getter
    private final Player player;

    @NotNull
    @Getter
    private final String creatorIp;

    @NotNull
    @Getter
    private final SequenceName sequenceName;

    @NotNull
    private final FakeplayerTicker ticker;

    @Getter
    @NotNull
    private final String name;

    @NotNull
    private final UUID uuid;

    @Getter
    @UnknownNullability
    private volatile NMSNetwork network;

    /**
     * @param creator      创建者
     * @param creatorIp    创建者 IP
     * @param sequenceName 序列名
     * @param lifespan     存活时间
     */
    public Fakeplayer(
            @NotNull CommandSender creator, @NotNull String creatorIp,
            @NotNull SequenceName sequenceName, long lifespan
    ) {
        this(creator, creator.getName(), creator instanceof Player p ? p.getUniqueId() : null,
                creatorIp, sequenceName, lifespan, null);
    }

    public Fakeplayer(
            @NotNull CommandSender creator, @NotNull String creatorName, @Nullable UUID creatorUuid,
            @NotNull String creatorIp, @NotNull SequenceName sequenceName, long lifespan
    ) {
        this(creator, creatorName, creatorUuid, creatorIp, sequenceName, lifespan, null);
    }

    public Fakeplayer(
            @NotNull CommandSender creator, @NotNull String creatorName, @Nullable UUID creatorUuid,
            @NotNull String creatorIp, @NotNull SequenceName sequenceName, long lifespan,
            @Nullable Location initialLocation
    ) {
        this.name = sequenceName.name();
        this.uuid = sequenceName.uuid();

        this.creator = creator;
        this.creatorName = creatorName.isBlank() ? "CONSOLE" : creatorName;
        this.creatorUuid = creatorUuid;
        this.creatorIp = creatorIp;
        this.sequenceName = sequenceName;
        var server = bridge.fromServer(Bukkit.getServer());
        this.handle = initialLocation != null && Main.getScheduler().isFolia()
                ? server.newPlayer(uuid, name, initialLocation)
                : server.newPlayer(uuid, name);
        this.player = handle.getPlayer();
        publishLocation();

        this.ticker = new FakeplayerTicker(this, lifespan);
        // Folia uses owner snapshots; Paper's player list owns its normal save path.
        this.player.setPersistent(!Main.getScheduler().isFolia() && config.isPersistData());
        this.player.setSleepingIgnored(true);
        this.handle.setPlayBefore(); // 可避免一些插件的第一次入服欢迎信息
        this.handle.disableAdvancements(Main.getInstance()); // 不提示成就信息
    }

    /**
     * 让假人诞生
     */
    public CompletableFuture<Void> spawnAsync(@NotNull SpawnOption option) {
        if (!Main.getInstance().isEnabled()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Plugin disabled before spawn"));
        if (!state.compareAndSet(State.INITIALIZING, State.JOINING)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Spawn already started"));
        }
        var address = ipGen.next();
        var savedNativeData = new AtomicReference<Object>();
        this.player.setMetadata(MetadataKeys.SPAWNED_AT, new FixedMetadataValue(Main.getInstance(), Bukkit.getCurrentTick()));
        return Main.getScheduler()
                .async(() -> {
                    var event = this.callPreLoginEvent(address);
                    if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                        throw new CommandException(translatable(
                                "fakeplayer.command.spawn.error.disallowed",
                                text(player.getName(), WHITE),
                                event.kickMessage()
                        ).color(RED));
                    }
                    return null;
                })
                .thenCompose(nul -> Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.PersistentFakeplayerManager.class).readNative(uuid, name))
                .thenCompose(nativeData -> Main.getScheduler().at(option.spawnAt(), () -> {
                    if (state.get() != State.JOINING) throw new java.util.concurrent.CancellationException("Spawn cancelled");
                    if (io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.requiresManualLoad()) {
                        io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.apply(player, nativeData, option.spawnAt());
                        savedNativeData.set(nativeData);
                    }
                    {
                        var event = this.callLoginEvent(address);
                        if (!event.allowed() && config.getPreventKicking().ordinal() < PreventKicking.ON_SPAWNING.ordinal()) {
                            throw new CommandException(translatable(
                                    "fakeplayer.command.spawn.error.disallowed", RED,
                                    text(player.getName(), WHITE),
                                    event.kickMessage()
                            ));
                        }
                    }

                    if (config.isDropInventoryOnQuiting()) {
                        // 跨服背包同步插件可能导致假人既丢弃了一份到地上，在重新生成的时候又回来了
                        // 因此在生成的时候清空一次背包
                        // 但无法解决登陆后延迟同步背包的情况
                        this.player.getInventory().clear();
                    }

                    FakeplayerFeatureUtils.setInvulnerable(this.player, option.invulnerable());
                    this.player.setCollidable(option.collidable());
                    this.player.setCanPickupItems(option.pickupItems());
                    if (option.lookAtEntity()) {
                        actionManager.setAction(player, ActionType.LOOK_AT_NEAREST_ENTITY, ActionSetting.continuous());
                    }
                    if (option.replenish()) {
                        replenishManager.setReplenish(player, true);
                    }
                    if (option.autofish()) {
                        autofishManager.setAutofish(player, true);
                    }
                    if (option.autosleep()) {
                        autosleepManager.setAutosleep(player, true);
                    }

                    this.network = bridge.createNetwork(address);
                    if (state.get() != State.JOINING || !Main.getInstance().isEnabled()) {
                        this.network.close();
                        throw new java.util.concurrent.CancellationException("Plugin disabled during join");
                    }
                    try { this.network.placeNewPlayer(Bukkit.getServer(), this.player, savedNativeData.get()); }
                    finally {
                        if (state.get() != State.JOINING || !Main.getInstance().isEnabled()) this.network.requestDisconnect();
                    }
                    this.player.getInventory().setHeldItemSlot(option.heldSlot());
                    this.player.setHealth(Optional.ofNullable(this.player.getAttribute(Attributes.maxHealth()))
                            .map(AttributeInstance::getValue)
                            .orElse(20D));    // 恢复生命值
                    this.player.setFoodLevel(20);
                    this.setupName();
                    this.handle.setupClientOptions();   // 处理皮肤设置问题

                }))
                .thenCompose(ignored -> option.skin() ? skinManager.useDefaultSkinAsync(creator, player).thenApply(applied -> (Void) null) : CompletableFuture.<Void>completedFuture(null))
                .thenCompose(ignored -> this.teleportToSpawnpoint(option.spawnAt().clone()))
                .thenCompose(ignored -> Main.getScheduler().runEntity(player, () -> {
                    if (!player.isOnline() || !state.compareAndSet(State.JOINING, State.ONLINE)) {
                        throw new IllegalStateException("Fakeplayer disconnected during spawn");
                    }
                    // Loading an older .dat may restore Bukkit.persist=false over our initial setting.
                    player.setPersistent(!Main.getScheduler().isFolia() && config.isPersistData());
                    if (io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.requiresManualLoad()) io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.restoreAttachments(player, savedNativeData.getAndSet(null));
                    publishLocation();
                    Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.PersistentFakeplayerManager.class).capture(player);
                    this.ticker.start();
                }));
    }

    /**
     * 将假人传送到指定位置
     *
     * @param to 目标位置
     */
    private CompletableFuture<Void> teleportToSpawnpoint(@NotNull Location to) {
        // Folia #421 invalidates the source player entity during a dimension change; owned
        // ender pearls tick in that window and would discard themselves as if their owner died.
        var shielded = new java.util.concurrent.atomic.AtomicBoolean();
        return Main.getScheduler().callEntity(player, () -> {
            var from = this.player.getLocation();
            if (!from.getWorld().equals(to.getWorld())) return CompletableFuture.completedFuture(true);
            // 如果生成世界等于目的世界, 则需要穿越一次维度才能获取刷怪能力
            var otherWorld = WorldUtils.getOtherWorld(from.getWorld());
            if (otherWorld == null) {
                Main.getScheduler().sender(creator, () -> this.creator.sendMessage(translatable(
                        "fakeplayer.command.spawn.error.no-mob-spawning-ability",
                        text(player.getName(), WHITE)
                ).color(GRAY)));
                return CompletableFuture.completedFuture(true);
            }
            if (Main.getScheduler().isFolia()) {
                shielded.set(io.github.hello09x.fakeplayer.core.platform.FoliaLifecycleHooks.shieldEnderPearls(player));
            }
            return Teleports.to(player, otherWorld.getSpawnLocation());
        }).thenCompose(future -> future)
                .thenCompose(detourSucceeded -> {
                    if (!detourSucceeded) return CompletableFuture.failedFuture(new IllegalStateException("Spawn dimension detour was cancelled"));
                    if (!shielded.get()) return Teleports.to(player, to);
                    // The transfer may replace the native player entity; shield the current owner too.
                    return Main.getScheduler().callEntity(player, () -> {
                        io.github.hello09x.fakeplayer.core.platform.FoliaLifecycleHooks.shieldEnderPearls(player);
                        return Teleports.to(player, to);
                    }).thenCompose(future -> future);
                })
                .thenAccept(success -> {
                    if (!success) throw new CommandException(translatable(
                            "fakeplayer.command.spawn.error.teleport-failed", text(name, WHITE)).color(RED));
                })
                .whenComplete((ignored, error) -> {
                    if (!shielded.get()) return;
                    Main.getScheduler().runEntity(player, () -> io.github.hello09x.fakeplayer.core.platform.FoliaLifecycleHooks.restoreEnderPearls(player))
                            .exceptionally(restoreError -> {
                                Main.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                                        "Cannot schedule ender pearl shield cleanup for " + name, restoreError);
                                return null;
                            });
                });
    }

    /** Must be called in the owning region, or at the initial location before joining. */
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        state.set(State.REMOVING);
        try {
            ticker.cancel();
            actionManager.stopOwned(player);
            if (network != null) network.close();
        } finally {
            state.set(State.REMOVED);
        }
    }

    /** Does not read the entity or unregister its connection from another region. */
    public void shutdownTransport() {
        state.set(State.REMOVING);
        var transport = network;
        if (transport != null) transport.requestDisconnect();
    }

    public boolean isOnline() {
        return this.player.isOnline();
    }

    public int getTickCount() {
        return handle.getTickCount();
    }

    private @NotNull AsyncPlayerPreLoginEvent callPreLoginEvent(@NotNull InetAddress address) {
        var event = new AsyncPlayerPreLoginEvent(
                this.name,
                address,
                this.uuid,
                false
        );
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    private @NotNull LoginResult callLoginEvent(@NotNull InetAddress address) {
        if (LoginCapabilities.MODERN) {
            return ModernLoginEvent.call(address);
        }
        return LegacyLoginEvent.call(player, address);
    }

    private record LoginResult(boolean allowed, @NotNull Component kickMessage) {
    }

    private static final class LoginCapabilities {
        private static final boolean MODERN = present();

        private static boolean present() {
            try {
                Class.forName("io.papermc.paper.connection.PlayerConnection", false, Fakeplayer.class.getClassLoader());
                Class.forName("io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent", false, Fakeplayer.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException ignored) { return false; }
        }
    }

    private static final class ModernLoginEvent {

        private static @NotNull LoginResult call(@NotNull InetAddress address) {
            var connection = new FakePlayerConnection(address);
            var event = new PlayerConnectionValidateLoginEvent(connection, Component.empty());
            Bukkit.getPluginManager().callEvent(event);
            var disconnectReason = connection.disconnectReason;
            return new LoginResult(
                    event.isAllowed() && disconnectReason == null,
                    disconnectReason == null ? event.getKickMessage() : disconnectReason
            );
        }

        private static final class FakePlayerConnection implements PlayerConnection {

            private final InetSocketAddress address;
            private @Nullable Component disconnectReason;

            private FakePlayerConnection(@NotNull InetAddress address) {
                this.address = new InetSocketAddress(address, 0);
            }

            @Override
            public void disconnect(@NotNull Component reason) {
                this.disconnectReason = reason;
            }

            @Override
            public boolean isTransferred() {
                return false;
            }

            @Override
            public @NotNull SocketAddress getAddress() {
                return address;
            }

            @Override
            public @NotNull InetSocketAddress getClientAddress() {
                return address;
            }

            @Override
            public @Nullable InetSocketAddress getVirtualHost() {
                return null;
            }

            @Override
            public @Nullable InetSocketAddress getHAProxyAddress() {
                return null;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static final class LegacyLoginEvent {

        private static @NotNull LoginResult call(@NotNull Player player, @NotNull InetAddress address) {
            var event = new PlayerLoginEvent(player, address.getHostAddress(), address);
            Bukkit.getPluginManager().callEvent(event);
            return new LoginResult(event.getResult() == PlayerLoginEvent.Result.ALLOWED, event.kickMessage());
        }
    }

    /**
     * 判断是否是创建者
     * <p>如果玩家下线再重新登陆, entityID 将会不一样导致 {@link Object#equals(Object)} 返回 {@code false}</p>
     *
     * @param sender 命令执行者
     * @return 是否是创建者
     */
    public boolean isCreatedBy(@NotNull CommandSender sender) {
        if (this.creatorUuid != null && sender instanceof Player ps) {
            return this.creatorUuid.equals(ps.getUniqueId());
        }
        return creatorName.equals(sender.getName());
    }

    public @NotNull UUID getUUID() {
        return uuid;
    }

    private void setupName() {
        var displayName = text(player.getName(), config.getNameStyleColor(), config.getNameStyleDecorations().toArray(new TextDecoration[0]));
        player.playerListName(displayName);
        player.displayName(displayName);
        player.customName(displayName);
    }
}
