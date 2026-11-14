package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.constant.MetadataKeys;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/**
 * @author fakeplayer
 * Manages automatic sleeping for fake players
 */
@Singleton
public class FakeplayerAutosleepManager implements Listener {

    private static final int BED_SEARCH_RADIUS = 8;
    private static final int BED_SEARCH_HEIGHT = 3;

    private final FakeplayerManager manager;
    private final Set<UUID> autosleepPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> noBedNotifiedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSleep = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private io.github.hello09x.fakeplayer.core.platform.TaskHandle task;

    @Inject
    public FakeplayerAutosleepManager(FakeplayerManager manager) {
        this.manager = manager;
    }

    public boolean isAutosleep(@NotNull Player fake) {
        return manager.isFake(fake) && fake.hasMetadata(MetadataKeys.AUTOSLEEP);
    }

    public void setAutosleep(@NotNull Player target, boolean autosleep) {
        if (manager.isNotFake(target)) {
            return;
        }

        if (!autosleep) {
            target.removeMetadata(MetadataKeys.AUTOSLEEP, Main.getInstance());
            autosleepPlayers.remove(target.getUniqueId());
            noBedNotifiedPlayers.remove(target.getUniqueId());
        } else {
            target.setMetadata(MetadataKeys.AUTOSLEEP, new FixedMetadataValue(Main.getInstance(), true));
            autosleepPlayers.add(target.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTimeSkip(@NotNull TimeSkipEvent event) {
        noBedNotifiedPlayers.clear();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        autosleepPlayers.remove(uuid);
        noBedNotifiedPlayers.remove(uuid);
    }

    /**
     * Checks periodically if it's night time and tries to sleep fakeplayers with autosleep enabled
     */
    public void checkAndSleep(@NotNull World world) {
        if (autosleepPlayers.isEmpty()) {
            return;
        }

        for (var fake : manager.getAll()) {
            Main.getScheduler().runEntity(fake, () -> {
                if (fake.getWorld().equals(world)) checkAndSleep(fake);
            });
        }
    }

    private void checkAndSleep(Player fake) {
        if (!fake.isOnline() || !isAutosleep(fake) || fake.isSleeping()) return;
        var world = fake.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        var time = world.getTime();
        if (time < 12542 || time > 23458) {
            noBedNotifiedPlayers.remove(fake.getUniqueId());
            return;
        }
        trySleepAsync(fake, true).exceptionally(error -> {
            Main.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Cannot prepare nearby beds for " + fake.getName(), error);
            return false;
        });
    }

    public java.util.concurrent.CompletableFuture<Boolean> trySleepAsync(Player fake, boolean notifyCreator) {
        if (!pendingSleep.add(fake.getUniqueId())) return java.util.concurrent.CompletableFuture.completedFuture(false);
        return io.github.hello09x.fakeplayer.core.platform.NearbyBlocks.use(fake, BED_SEARCH_RADIUS + 1,
                () -> fake.isOnline() && !fake.isDead() && trySleep(fake, notifyCreator))
                .whenComplete((success, error) -> pendingSleep.remove(fake.getUniqueId()));
    }

    public boolean trySleep(@NotNull Player fake, boolean notifyCreator) {
        var bed = findAvailableBed(fake);

        if (bed == null) {
            if (notifyCreator) {
                notifyNoBed(fake);
            }
            return false;
        }

        if (fake.sleep(bed.getLocation(), true)) {
            noBedNotifiedPlayers.remove(fake.getUniqueId());
            return true;
        }

        if (notifyCreator) {
            notifyNoBed(fake);
        }
        return false;
    }

    private Block findAvailableBed(@NotNull Player fake) {
        var center = fake.getLocation().getBlock();
        for (int y = -BED_SEARCH_HEIGHT; y <= BED_SEARCH_HEIGHT; y++) {
            for (int x = -BED_SEARCH_RADIUS; x <= BED_SEARCH_RADIUS; x++) {
                for (int z = -BED_SEARCH_RADIUS; z <= BED_SEARCH_RADIUS; z++) {
                    var bed = getBedHead(center.getRelative(x, y, z));
                    if (bed == null) {
                        continue;
                    }

                    var data = (Bed) bed.getBlockData();
                    if (!data.isOccupied()) {
                        return bed;
                    }
                }
            }
        }
        return null;
    }

    private Block getBedHead(@NotNull Block block) {
        if (!Main.getScheduler().owns(block.getLocation())) return null;
        if (!(block.getBlockData() instanceof Bed data)) {
            return null;
        }

        if (data.getPart() == Bed.Part.HEAD) {
            return block;
        }

        var head = block.getRelative(data.getFacing());
        if (!Main.getScheduler().owns(head.getLocation())) return null;
        return head.getBlockData() instanceof Bed headData && headData.getPart() == Bed.Part.HEAD
                ? head
                : null;
    }

    private void notifyNoBed(@NotNull Player fake) {
        if (!noBedNotifiedPlayers.add(fake.getUniqueId())) {
            return;
        }

        CommandSender creator = manager.getCreator(fake);
        if (creator != null) {
            Main.getScheduler().sender(creator, () -> creator.sendMessage(translatable(
                    "fakeplayer.command.sleep.error.no-bed",
                    text(fake.getName(), WHITE)
            ).color(GRAY)));
        }
    }

    /**
     * Schedule a task to check for night time and auto sleep
     */
    public void startScheduler() {
        if (task != null) {
            return;
        }

        task = Main.getScheduler().globalTimer(() -> {
            for (var fake : manager.getAll()) {
                if (autosleepPlayers.contains(fake.getUniqueId())) {
                    Main.getScheduler().runEntity(fake, () -> checkAndSleep(fake));
                }
            }
        }, 100, 100);
    }

    public void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        autosleepPlayers.clear();
        noBedNotifiedPlayers.clear();
    }
}
