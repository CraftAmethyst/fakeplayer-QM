package io.github.hello09x.fakeplayer.core.manager.invsee;

import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.TransferMailbox;
import io.github.hello09x.fakeplayer.core.platform.PlatformScheduler;
import io.github.hello09x.fakeplayer.core.platform.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryEditorTest {
    MockedStatic<Main> mainStatic;
    MockedStatic<Bukkit> bukkitStatic;
    Player viewer, target;
    PlayerInventory targetInventory, viewerInventory;
    Inventory top;
    InventoryView view;
    InventoryEditor editor;
    PlatformScheduler scheduler;
    ItemStack[] targetItems = new ItemStack[36], viewerItems = new ItemStack[41];
    final AtomicReference<ItemStack> cursor = new AtomicReference<>();
    final List<TransferMailbox.Delivery> receipts = new ArrayList<>();
    Supplier<?> deferred;
    CompletableFuture<Object> deferredResult;
    boolean deferTarget;

    @BeforeEach void setUp() {
        mainStatic = mockStatic(Main.class); bukkitStatic = mockStatic(Bukkit.class);
        var main = mock(Main.class);
        when(main.getLogger()).thenReturn(Logger.getLogger("inventory-editor-test"));
        mainStatic.when(Main::getInstance).thenReturn(main);
        scheduler = mock(PlatformScheduler.class);
        mainStatic.when(Main::getScheduler).thenReturn(scheduler);
        viewer = mock(Player.class); target = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID()); when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.isOp()).thenReturn(true); when(viewer.isOnline()).thenReturn(true); when(target.isOnline()).thenReturn(true);
        when(target.getName()).thenReturn("fake");
        viewerInventory = inventory(viewerItems); targetInventory = inventory(targetItems);
        when(viewer.getInventory()).thenReturn(viewerInventory); when(target.getInventory()).thenReturn(targetInventory);
        when(viewer.getItemOnCursor()).thenAnswer(ignored -> cursor.get());
        doAnswer(invocation -> { cursor.set(invocation.getArgument(0)); return null; }).when(viewer).setItemOnCursor(any());
        when(scheduler.callEntity(any(Entity.class), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            if (deferTarget && invocation.getArgument(0) == target) {
                deferred = action; deferredResult = new CompletableFuture<>(); return deferredResult;
            }
            try { return CompletableFuture.completedFuture(action.get()); }
            catch (Throwable error) { return CompletableFuture.failedFuture(error); }
        });
        when(scheduler.runEntity(any(Entity.class), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run(); return CompletableFuture.completedFuture(null);
        });
        when(scheduler.entityTimer(any(), any(), anyLong(), anyLong(), any())).thenReturn(mock(TaskHandle.class));
        var manager = mock(FakeplayerManager.class); when(manager.isFake(target)).thenReturn(true);
        var mailbox = mock(TransferMailbox.class);
        doAnswer(invocation -> { receipts.add(invocation.getArgument(1)); return null; }).when(mailbox).credit(any(), any());
        top = mock(Inventory.class); view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(viewer.openInventory(top)).thenReturn(view);
        var holder = new AtomicReference<InventoryHolder>();
        when(top.getHolder()).thenAnswer(ignored -> holder.get());
        bukkitStatic.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(36), any(net.kyori.adventure.text.Component.class)))
                .thenAnswer(invocation -> { holder.set(invocation.getArgument(0)); return top; });
        editor = new InventoryEditor(manager, mailbox);
    }

    @AfterEach void tearDown() {
        try { if (editor != null) editor.close(); }
        finally { if (bukkitStatic != null) bukkitStatic.close(); if (mainStatic != null) mainStatic.close(); }
    }

    @Test void closingBlockInventoryDoesNotReloadItsHolderDuringChunkUnload() {
        var chest = mock(Inventory.class);
        when(chest.getLocation()).thenReturn(new org.bukkit.Location(mock(org.bukkit.World.class), 0, 64, 0));
        when(chest.getHolder()).thenThrow(new IllegalStateException("Chunk is unloading"));
        var event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(chest);
        assertDoesNotThrow(() -> editor.onClose(event));
        verify(chest, never()).getHolder();
    }

    PlayerInventory inventory(ItemStack[] items) {
        var inventory = mock(PlayerInventory.class);
        when(inventory.getStorageContents()).thenAnswer(ignored -> Arrays.copyOf(items, 36));
        when(inventory.getItem(anyInt())).thenAnswer(invocation -> items[(int) invocation.getArgument(0)]);
        doAnswer(invocation -> { items[(int) invocation.getArgument(0)] = invocation.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), any());
        return inventory;
    }

    InventoryClickEvent click(InventoryAction action, int raw, int slot) {
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view); when(event.getAction()).thenReturn(action);
        when(event.getRawSlot()).thenReturn(raw); when(event.getSlot()).thenReturn(slot);
        when(event.getCursor()).thenAnswer(ignored -> cursor.get());
        when(event.getCurrentItem()).thenAnswer(ignored -> raw < 36 ? targetItems[raw] : viewerItems[slot]);
        return event;
    }

    @Test void hotbarSwapReturnsWithdrawnStackToTheSameHotbarSlot() {
        targetItems[0] = item(Material.DIAMOND, 3); viewerItems[2] = item(Material.STONE, 4);
        assertTrue(editor.open(viewer, target).join());
        var event = click(InventoryAction.HOTBAR_SWAP, 0, 0); when(event.getHotbarButton()).thenReturn(2);
        editor.onClick(event);
        assertEquals(item(Material.STONE, 4), targetItems[0]); assertNull(viewerItems[2]);
        assertEquals(item(Material.DIAMOND, 3), receipts.get(0).slots().get(2));
        verify(event).setCancelled(true);
    }

    @Test void shiftWithdrawOnlyTakesWhatFits() {
        Arrays.fill(viewerItems, item(Material.STONE, 64)); viewerItems[8] = item(Material.DIAMOND, 62);
        targetItems[0] = item(Material.DIAMOND, 10); assertTrue(editor.open(viewer, target).join());
        editor.onClick(click(InventoryAction.MOVE_TO_OTHER_INVENTORY, 0, 0));
        assertEquals(item(Material.DIAMOND, 8), targetItems[0]);
        assertEquals(item(Material.DIAMOND, 64), receipts.get(0).slots().get(8));
    }

    @Test void concurrentTargetConsumptionRejectsDepositAndRefundsCursor() {
        targetItems[0] = item(Material.DIAMOND, 10); cursor.set(item(Material.DIAMOND, 2));
        assertTrue(editor.open(viewer, target).join()); targetItems[0] = item(Material.DIAMOND, 9);
        editor.onClick(click(InventoryAction.PLACE_ALL, 0, 0));
        assertEquals(item(Material.DIAMOND, 9), targetItems[0]);
        assertEquals(List.of(item(Material.DIAMOND, 2)), receipts.get(0).items()); assertTrue(receipts.get(0).cursor());
    }

    @Test void closeBeforeTargetCommitRefundsOnceAndPreventsLateDebit() {
        targetItems[0] = item(Material.DIAMOND, 10); cursor.set(item(Material.STONE, 2));
        assertTrue(editor.open(viewer, target).join()); deferTarget = true;
        editor.onClick(click(InventoryAction.SWAP_WITH_CURSOR, 0, 0));
        editor.close(); deferredResult.complete(deferred.get());
        assertEquals(item(Material.DIAMOND, 10), targetItems[0]);
        assertEquals(1, receipts.size()); assertEquals(List.of(item(Material.STONE, 2)), receipts.get(0).items());
    }

    @Test void theSameEditorCanReuseAnOpenSession() {
        assertTrue(editor.open(viewer, target).join());
        assertTrue(editor.open(viewer, target).join());
        verify(scheduler, never()).sender(eq(viewer), any(Runnable.class));
    }

    @Test void aDifferentEditorCannotOpenTheSameTarget() {
        var other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.isOp()).thenReturn(true);
        assertTrue(editor.open(viewer, target).join());
        assertFalse(editor.open(other, target).join());
        verify(scheduler).sender(eq(other), any(Runnable.class));
    }

    @Test void staleClosedViewCannotWithdrawCopiedItems() {
        targetItems[0] = item(Material.DIAMOND, 10); assertTrue(editor.open(viewer, target).join()); editor.close();
        var event = click(InventoryAction.PICKUP_ALL, 0, 0); editor.onClick(event);
        verify(event).setCancelled(true); assertEquals(0, receipts.size());
        assertEquals(item(Material.DIAMOND, 10), targetItems[0]);
    }

    static ItemStack item(Material material, int amount) { return new Stack(material, amount); }

    /** Quantity and similarity model; real CraftItemStack/NBT behavior is covered by server acceptance. */
    static final class Stack extends ItemStack {
        final Material material; int amount;
        Stack(Material material, int amount) { super(); this.material = material; this.amount = amount; }
        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return 64; }
        @Override public Stack clone() { return new Stack(material, amount); }
        @Override public boolean isSimilar(ItemStack other) { return other != null && material == other.getType(); }
        @Override public boolean equals(Object other) { return other instanceof Stack stack && material == stack.material && amount == stack.amount; }
        @Override public int hashCode() { return Objects.hash(material, amount); }
        @Override public String toString() { return material + ":" + amount; }
    }
}
