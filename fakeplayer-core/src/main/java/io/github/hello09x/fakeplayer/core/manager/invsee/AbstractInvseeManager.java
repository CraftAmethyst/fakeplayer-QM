package io.github.hello09x.fakeplayer.core.manager.invsee;

import io.github.hello09x.fakeplayer.core.manager.FakeplayerList;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/**
 * @author tanyaofei
 * @since 2024/8/12
 **/
public abstract class AbstractInvseeManager implements InvseeManager {

    protected final FakeplayerManager manager;
    protected final FakeplayerList fakeplayerList;

    protected AbstractInvseeManager(FakeplayerManager manager, FakeplayerList fakeplayerList) {
        this.manager = manager;
        this.fakeplayerList = fakeplayerList;
    }

    @Override
    public boolean invsee(@NotNull Player viewer, @NotNull Player whom) {
        var result = invseeAsync(viewer, whom);
        return result.isDone() ? result.getNow(false) : true;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> invseeAsync(Player viewer, Player whom) {
        return io.github.hello09x.fakeplayer.core.Main.getInjector().getInstance(InventoryEditor.class).open(viewer, whom);
    }

    protected abstract @Nullable InventoryView openInventory(
            @NotNull Player viewer,
            @NotNull Player whom,
            @NotNull Component title
    );

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void rightClickToInvsee(@NotNull PlayerInteractAtEntityEvent event) {
        if (!((event.getRightClicked()) instanceof Player whom)) {
            return;
        }

        this.invsee(event.getPlayer(), whom);   // fakeplayer check here
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void fixDragInventory(@NotNull InventoryDragEvent event) {
        var top = event.getView().getTopInventory();
        if (top.getType() == InventoryType.PLAYER && top.getHolder() instanceof Player whom && manager.isFake(whom)) {
            if (event.getNewItems().keySet().stream().anyMatch(slot -> slot > 35)) {    // > 35 表示从假人背包拖动到玩家背包, 这种操作会出现问题
                event.setCancelled(true);
            }
        }
    }

}
