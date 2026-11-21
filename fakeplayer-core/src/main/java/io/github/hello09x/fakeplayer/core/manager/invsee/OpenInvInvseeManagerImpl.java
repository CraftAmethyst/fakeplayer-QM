package io.github.hello09x.fakeplayer.core.manager.invsee;

import com.google.common.base.Throwables;
import com.lishid.openinv.IOpenInv;
import com.lishid.openinv.internal.ISpecialInventory;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerList;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * @author tanyaofei
 * @since 2024/8/12
 **/
public class OpenInvInvseeManagerImpl extends AbstractInvseeManager {

    private final static Logger log = Main.getInstance().getLogger();
    private final IOpenInv openInv;

    public OpenInvInvseeManagerImpl(FakeplayerManager manager, FakeplayerList fakeplayerList) {
        super(manager, fakeplayerList);
        this.openInv = (IOpenInv) Bukkit.getPluginManager().getPlugin("OpenInv");
    }

    @Override
    public CompletableFuture<Boolean> invseeAsync(@NotNull Player viewer, @NotNull Player whom) {
        if (viewer.equals(whom) || !manager.isFake(whom)) return CompletableFuture.completedFuture(false);
        var creatorId = manager.getCreatorUuid(whom);
        if (!viewer.isOp() && !viewer.getUniqueId().equals(creatorId)) return CompletableFuture.completedFuture(false);

        var specialInventory = Main.getScheduler().callEntity(whom, () -> {
            if (!whom.isOnline()) return null;
            try {
                return openInv.getSpecialInventory(whom, true);
            } catch (InstantiationException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
        return specialInventory.thenCompose(inventory -> {
            if (inventory == null) return CompletableFuture.completedFuture(false);
            return Main.getScheduler().callEntity(viewer, () ->
                    viewer.isOnline() && openInvInventory(viewer, inventory) != null);
        });
    }

    @Override
    protected InventoryView openInventory(
            @NotNull Player viewer,
            @NotNull Player whom,
            @NotNull Component title
    ) {
        try {
            return openInvInventory(viewer, openInv.getSpecialInventory(whom, true));
        } catch (InstantiationException e) {
            log.warning("Failed to %s's open inventory for %s\n%s".formatted(whom.getName(), viewer.getName(), Throwables.getStackTraceAsString(e)));
            return null;
        }
    }

    private InventoryView openInvInventory(Player viewer, ISpecialInventory inventory) {
        return openInv.openInventory(viewer, inventory, false);
    }
}
