package io.github.hello09x.fakeplayer.core.manager.invsee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class InvseeInventoryHolder implements InventoryHolder {

    private final UUID targetId;
    private Inventory inventory;

    public InvseeInventoryHolder(@NotNull UUID targetId) {
        this.targetId = targetId;
    }

    public @NotNull UUID getTargetId() {
        return targetId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }
}
