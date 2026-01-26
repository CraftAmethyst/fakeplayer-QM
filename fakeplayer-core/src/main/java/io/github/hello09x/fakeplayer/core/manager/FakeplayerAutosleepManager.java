package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.devtools.core.utils.BlockUtils;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.constant.MetadataKeys;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author fakeplayer
 * Manages automatic sleeping for fake players
 */
@Singleton
public class FakeplayerAutosleepManager implements Listener {

    private final FakeplayerManager manager;

    @Inject
    public FakeplayerAutosleepManager(FakeplayerManager manager) {
        this.manager = manager;
    }

    public boolean isAutosleep(@NotNull Player fake) {
        return fake.hasMetadata(MetadataKeys.AUTOSLEEP);
    }

    public void setAutosleep(@NotNull Player target, boolean autosleep) {
        if (!autosleep) {
            target.removeMetadata(MetadataKeys.AUTOSLEEP, Main.getInstance());
        } else {
            target.setMetadata(MetadataKeys.AUTOSLEEP, new FixedMetadataValue(Main.getInstance(), true));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTimeSkip(@NotNull TimeSkipEvent event) {
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }

        // When time is skipped due to night, all players slept successfully
        // We can use this to know when to re-enable auto sleep for next night
    }

    /**
     * Checks periodically if it's night time and tries to sleep fakeplayers with autosleep enabled
     */
    public void checkAndSleep(@NotNull World world) {
        long time = world.getTime();
        // Night time is from 12542 to 23458 ticks
        if (time < 12542 || time > 23458) {
            return;
        }

        // Check all fakeplayers in this world with autosleep enabled
        for (Player fake : manager.getAll()) {
            if (!fake.getWorld().equals(world)) {
                continue;
            }

            if (!isAutosleep(fake)) {
                continue;
            }

            if (fake.isSleeping()) {
                continue;
            }

            // Try to find a nearby bed and sleep
            trySleep(fake);
        }
    }

    private void trySleep(@NotNull Player fake) {
        var bed = BlockUtils.getNearbyBlock(fake.getLocation(), 4, block -> {
            if (!(block.getBlockData() instanceof Bed data)) {
                return false;
            }

            return !data.isOccupied() && data.getPart() == Bed.Part.HEAD;
        });

        if (bed == null) {
            return;
        }

        fake.sleep(bed.getLocation(), false);
    }

    /**
     * Schedule a task to check for night time and auto sleep
     */
    public void startScheduler() {
        Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() != World.Environment.NORMAL) {
                    continue;
                }
                checkAndSleep(world);
            }
        }, 100L, 100L); // Check every 5 seconds (100 ticks)
    }
}
