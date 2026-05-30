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

    public void save() {
        if (!config.isPersistData()) {
            if (file.exists() && !file.delete()) {
                log.warning("Failed to delete persistent fakeplayer file: " + file);
            }
            return;
        }

        var data = new YamlConfiguration();
        var players = manager.getAll();
        for (int i = 0; i < players.size(); i++) {
            save(data.createSection("fakeplayers." + i), players.get(i));
        }

        try {
            data.save(file);
        } catch (IOException e) {
            log.warning("Failed to save persistent fakeplayers: " + e.getMessage());
        }
    }

    private void save(@NotNull ConfigurationSection section, @NotNull Player player) {
        var location = player.getLocation();
        section.set("name", player.getName());
        section.set("uuid", player.getUniqueId().toString());
        section.set("creator.name", Optional.ofNullable(manager.getCreatorName(player)).orElse("CONSOLE"));
        section.set("creator.uuid", stringify(manager.getCreatorUuid(player)));
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
        section.set("held-slot", player.getInventory().getHeldItemSlot());

        var features = section.createSection("features");
        for (var feature : Feature.values()) {
            if (feature.hasDetector()) {
                features.set(feature.name(), feature.getDetector().apply(player));
            }
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
            if (!online.getUniqueId().equals(uuid) && online.getAddress() != null) {
                log.warning("Skipped persistent fakeplayer '%s': a player with the same name is already online".formatted(name));
                return;
            }
            online.kick(net.kyori.adventure.text.Component.text(FakeplayerManager.REMOVAL_REASON_PREFIX + "stale persistent fakeplayer"));
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> restore(section), 1);
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
            log.warning("Failed to restore persistent fakeplayer '%s': %s".formatted(name, throwable.getMessage()));
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
