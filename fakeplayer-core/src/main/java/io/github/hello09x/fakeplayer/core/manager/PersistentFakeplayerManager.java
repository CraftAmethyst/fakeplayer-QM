package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@Singleton
public class PersistentFakeplayerManager {

    private static final Logger log = Main.getInstance().getLogger();

    private final FakeplayerManager manager;
    private final FakeplayerConfig config;
    private final File file;

    @Inject
    public PersistentFakeplayerManager(FakeplayerManager manager, FakeplayerConfig config) {
        this.manager = manager;
        this.config = config;
        this.file = new File(Main.getInstance().getDataFolder(), "persistent-fakeplayers.yml");
    }

    /** Published only by the player's owner. No live entity is read by the writer. */
    private record SavedPlayer(String name, UUID uuid, String creatorName, UUID creatorUuid,
                               String world, double x, double y, double z, float yaw, float pitch,
                               int heldSlot, Map<Feature, String> features,
                               io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.Snapshot nativeData) {}

    private final java.util.concurrent.ConcurrentMap<UUID, SavedPlayer> snapshots = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService writer = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "fakeplayer-persistence");
        thread.setDaemon(true);
        return thread;
    });

    public void capture(Player player) {
        if (!manager.isFake(player)) return;
        var location = player.getLocation();
        var features = new EnumMap<Feature, String>(Feature.class);
        for (var feature : Feature.values()) {
            if (feature.hasDetector()) features.put(feature, feature.getDetector().apply(player));
        }
        snapshots.put(player.getUniqueId(), new SavedPlayer(player.getName(), player.getUniqueId(),
                Optional.ofNullable(manager.getCreatorName(player)).orElse("CONSOLE"), manager.getCreatorUuid(player),
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
                player.getInventory().getHeldItemSlot(), Map.copyOf(features),
                Main.getScheduler().isFolia() && config.isPersistData()
                        ? io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.capture(player) : null));
    }

    /** Ordered after previous native writes, so a quick rejoin cannot read stale data. */
    public java.util.concurrent.CompletableFuture<Object> readNative(UUID id, String name) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
                () -> io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.requiresManualLoad() && config.isPersistData()
                        ? io.github.hello09x.fakeplayer.core.platform.FoliaPlayerData.read(id, name) : null, writer);
    }

    public void forget(UUID uuid) {
        if (!Bukkit.getServer().isStopping()) {
            var removed = snapshots.remove(uuid);
            if (removed != null && removed.nativeData() != null) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try { removed.nativeData().write(); }
                    catch (IOException error) { throw new java.io.UncheckedIOException(error); }
                }, writer).exceptionally(error -> {
                    log.log(java.util.logging.Level.SEVERE, "Failed to save departing fakeplayer " + uuid, error);
                    return null;
                });
            }
        }
    }

    public java.util.concurrent.CompletableFuture<Void> saveAsync() {
        var saved = java.util.List.copyOf(snapshots.values());
        var enabled = config.isPersistData();
        return java.util.concurrent.CompletableFuture.runAsync(() -> write(saved, enabled), writer);
    }

    public void start() {
        Main.getScheduler().globalTimer(() -> saveAsync().exceptionally(error -> {
            log.log(java.util.logging.Level.SEVERE, "Failed to checkpoint persistent fakeplayers", error);
            return null;
        }), 1200, 1200);
    }

    /** Shutdown waits only for serial file IO, never for a region or entity task. */
    public void save() {
        try { saveAsync().join(); }
        finally { writer.shutdown(); }
    }

    private void write(java.util.List<SavedPlayer> saved, boolean enabled) {
        try {
            if (!enabled) {
                java.nio.file.Files.deleteIfExists(file.toPath());
                return;
            }
            var data = new YamlConfiguration();
            for (int i = 0; i < saved.size(); i++) {
                var player = saved.get(i);
                if (player.nativeData() != null) player.nativeData().write();
                var section = data.createSection("fakeplayers." + i);
                section.set("name", player.name());
                section.set("uuid", player.uuid().toString());
                section.set("creator.name", player.creatorName());
                section.set("creator.uuid", stringify(player.creatorUuid()));
                section.set("world", player.world());
                section.set("x", player.x()); section.set("y", player.y()); section.set("z", player.z());
                section.set("yaw", player.yaw()); section.set("pitch", player.pitch());
                section.set("held-slot", player.heldSlot());
                var features = section.createSection("features");
                player.features().forEach((key, value) -> features.set(key.name(), value));
            }
            java.nio.file.Files.createDirectories(file.toPath().getParent());
            var temporary = java.nio.file.Files.createTempFile(file.toPath().getParent(), "persistent-", ".yml");
            try {
                data.save(temporary.toFile());
                try { java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { java.nio.file.Files.deleteIfExists(temporary); }
        } catch (IOException error) {
            throw new java.io.UncheckedIOException("Failed to save persistent fakeplayers", error);
        }
    }

    public void restore() {
        if (!config.isPersistData() || !file.exists()) {
            return;
        }

        var data = YamlConfiguration.loadConfiguration(file);
        var root = data.getConfigurationSection("fakeplayers");
        if (root == null) {
            return;
        }

        for (var key : root.getKeys(false)) {
            var section = root.getConfigurationSection(key);
            if (section != null) {
                restore(section);
            }
        }
    }

    private void restore(@NotNull ConfigurationSection section) {
        var name = section.getString("name");
        var uuid = parseUUID(section.getString("uuid"));
        var worldName = section.getString("world");
        var creatorName = section.getString("creator.name", "CONSOLE");
        if (name == null || uuid == null || worldName == null) {
            return;
        }

        var online = Bukkit.getPlayerExact(name);
        if (online != null) {
            if (manager.isFake(online)) {
                return;
            }
            log.warning("Skipped persistent fakeplayer '%s': a player with this name is already online".formatted(name));
            return;
        }

        var world = Bukkit.getWorld(worldName);
        if (world == null) {
            log.warning("Skipped persistent fakeplayer '%s': world '%s' is not loaded".formatted(name, worldName));
            return;
        }

        var location = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );

        manager.restoreAsync(
                creatorName,
                parseUUID(section.getString("creator.uuid")),
                uuid,
                name,
                location,
                loadFeatures(section.getConfigurationSection("features")),
                section.getInt("held-slot", 0)
        ).exceptionally(throwable -> {
            log.log(java.util.logging.Level.WARNING, "Failed to restore persistent fakeplayer '" + name + "'", throwable);
            return null;
        });
    }

    private @NotNull Map<Feature, String> loadFeatures(@Nullable ConfigurationSection section) {
        var features = new EnumMap<Feature, String>(Feature.class);
        if (section == null) {
            return features;
        }

        for (var feature : Feature.values()) {
            var value = section.getString(feature.name());
            if (value != null && feature.getOptions().contains(value)) {
                features.put(feature, value);
            }
        }
        return features;
    }

    private static @Nullable String stringify(@Nullable UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static @Nullable UUID parseUUID(@Nullable String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
