package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;

/** Holds refunds and receipts when their recipient retires before acknowledgement. */
@Singleton
public final class TransferMailbox implements Listener, AutoCloseable {
    public record Delivery(UUID id, List<ItemStack> items, int experience, boolean cursor, Map<Integer, ItemStack> slots, boolean drop) {
        public Delivery {
            id = id == null ? UUID.randomUUID() : id;
            items = items.stream().filter(Objects::nonNull).filter(item -> !TransferMailbox.empty(item))
                    .map(ItemStack::clone).toList();
            var copied = new HashMap<Integer, ItemStack>();
            slots.forEach((slot, item) -> {
                if (slot < 0 || slot > 40) throw new IllegalArgumentException("Invalid inventory slot");
                if (!TransferMailbox.empty(item)) copied.put(slot, item.clone());
            });
            slots = Map.copyOf(copied);
            if (experience < 0) throw new IllegalArgumentException("Negative experience");
        }
        public Delivery(List<ItemStack> items, int experience, boolean cursor, Map<Integer, ItemStack> slots, boolean drop) {
            this(UUID.randomUUID(), items, experience, cursor, slots, drop);
        }
        public Delivery(List<ItemStack> items, int experience, boolean cursor) { this(UUID.randomUUID(), items, experience, cursor, Map.of(), false); }
        public static Delivery items(Collection<ItemStack> items, boolean cursor) { return new Delivery(new ArrayList<>(items), 0, cursor); }
        public static Delivery inventory(Collection<ItemStack> items, boolean cursor, Map<Integer, ItemStack> slots, boolean drop) {
            return new Delivery(new ArrayList<>(items), 0, cursor, slots, drop);
        }
        public static Delivery experience(int amount) { return new Delivery(List.of(), amount, false); }
        public boolean empty() { return items.isEmpty() && slots.isEmpty() && experience == 0; }
    }

    private static boolean empty(ItemStack item) {
        if (item == null || item.getAmount() <= 0) return true;
        var type = item.getType();
        return type == org.bukkit.Material.AIR || type == org.bukkit.Material.CAVE_AIR || type == org.bukkit.Material.VOID_AIR;
    }

    private final Map<UUID, List<Delivery>> pending = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> durability = new HashMap<>();
    private final File file = new File(Main.getInstance().getDataFolder(), "pending-transfers.yml");
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "fakeplayer-transfer-mailbox"); thread.setDaemon(true); return thread;
    });
    private boolean closed;
    private CompletableFuture<Void> receiptIdsDurable = CompletableFuture.completedFuture(null);

    @Inject public TransferMailbox() {
        var data = YamlConfiguration.loadConfiguration(file);
        var rewriteReceiptIds = false;
        for (var key : data.getKeys(false)) {
            var deliveries = new ArrayList<Delivery>();
            var section = data.getConfigurationSection(key);
            if (section == null) continue;
            for (var entry : section.getKeys(false)) {
                var saved = section.getConfigurationSection(entry);
                if (saved == null) continue;
                var items = saved.getList("items", List.of()).stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast).toList();
                var slots = new HashMap<Integer, ItemStack>();
                var slotData = saved.getConfigurationSection("slots");
                if (slotData != null) for (var slot : slotData.getKeys(false)) {
                    var item = slotData.getItemStack(slot);
                    if (item != null) slots.put(Integer.parseInt(slot), item);
                }
                var storedId = saved.getString("id");
                var receiptId = parseReceiptId(storedId);
                if (storedId == null || !receiptId.toString().equalsIgnoreCase(storedId)) rewriteReceiptIds = true;
                deliveries.add(new Delivery(receiptId, items, saved.getInt("experience"),
                        saved.getBoolean("cursor"), slots, saved.getBoolean("drop")));
            }
            pending.put(UUID.fromString(key), deliveries);
        }
        if (rewriteReceiptIds) receiptIdsDurable = persist();
        Main.getScheduler().globalTimer(() -> {
            java.util.Set<UUID> recipients;
            synchronized (this) { recipients = java.util.Set.copyOf(pending.keySet()); }
            for (var id : recipients) { var player = org.bukkit.Bukkit.getPlayer(id); if (player != null) deliverLater(player); }
        }, 20, 20);
    }

    public synchronized void credit(UUID recipient, Delivery delivery) {
        if (delivery == null || delivery.empty()) return;
        if (closed) throw new IllegalStateException("Mailbox already closed");
        var queue = pending.computeIfAbsent(recipient, ignored -> new ArrayList<>());
        queue.add(delivery);
        persistPending();
    }

    /** The mailbox lock serializes source debit and receipt enqueue against shutdown. */
    public synchronized int transferExperience(Player source, UUID recipient) {
        if (closed || !source.isOnline()) throw new IllegalStateException("Experience source unavailable");
        var amount = io.github.hello09x.devtools.core.utils.ExperienceUtils.getExp(source);
        if (amount == 0) return 0;
        io.github.hello09x.devtools.core.utils.ExperienceUtils.clean(source);
        credit(recipient, Delivery.experience(amount));
        return amount;
    }

    public void deliverLater(Player recipient) {
        Main.getScheduler().runEntity(recipient, () -> deliver(recipient));
    }

    /** Called only by the recipient's owner; disconnect leaves the delivery in the mailbox. */
    public synchronized void deliver(Player recipient) {
        if (closed || !recipient.isOnline()) return;
        if (!receiptIdsDurable.isDone() || receiptIdsDurable.isCompletedExceptionally()) {
            if (receiptIdsDurable.isCompletedExceptionally()) receiptIdsDurable = persist();
            receiptIdsDurable.whenComplete((ignored, error) -> {
                if (error == null) Main.getScheduler().runEntity(recipient, () -> deliver(recipient));
            });
            return;
        }
        var recipientId = recipient.getUniqueId();
        var queued = pending.get(recipientId);
        if (queued == null) return;
        var writes = queued.stream().map(delivery -> durability.get(delivery.id())).filter(Objects::nonNull).toList();
        if (writes.stream().anyMatch(CompletableFuture::isCompletedExceptionally)) {
            retryPendingPersistence(recipientId, recipient);
            return;
        }
        if (writes.stream().anyMatch(write -> !write.isDone())) {
            CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
                if (error == null) deliverLater(recipient);
                else retryPendingPersistence(recipientId, recipient);
            });
            return;
        }
        var deliveries = pending.remove(recipientId);
        deliveries.forEach(delivery -> durability.remove(delivery.id()));
        var alreadyApplied = new ArrayList<UUID>();
        var toDeliver = new ArrayList<Delivery>();
        for (var delivery : deliveries) {
            if (!delivery.drop() && io.github.hello09x.fakeplayer.core.platform.TransferReceiptMarker.isApplied(recipient, delivery.id())) {
                alreadyApplied.add(delivery.id());
            } else {
                toDeliver.add(delivery);
            }
        }
        var original = Arrays.stream(recipient.getInventory().getContents()).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        var originalCursor = recipient.getItemOnCursor().clone();
        var originalExp = io.github.hello09x.devtools.core.utils.ExperienceUtils.getExp(recipient);
        var drops = new ArrayList<ItemStack>();
        var newlyApplied = new ArrayList<UUID>();
        try {
            var remainder = new ArrayList<Delivery>();
            for (var delivery : toDeliver) {
                var displaced = new ArrayList<ItemStack>();
                for (var entry : delivery.slots().entrySet()) {
                    var current = recipient.getInventory().getItem(entry.getKey());
                    if (empty(current)) recipient.getInventory().setItem(entry.getKey(), entry.getValue().clone());
                    else displaced.add(entry.getValue());
                }
                var cursor = delivery.cursor();
                var leftovers = new ArrayList<ItemStack>();
                var allItems = new ArrayList<>(delivery.items()); allItems.addAll(displaced);
                for (var item : allItems) {
                    var copy = item.clone();
                    if (delivery.drop()) {
                        // A cancelled drop is handled by Bukkit and returned to the owner's inventory.
                        drops.add(copy);
                    } else if (cursor && empty(recipient.getItemOnCursor())) recipient.setItemOnCursor(copy);
                    else leftovers.addAll(recipient.getInventory().addItem(copy).values());
                    cursor = false;
                }
                var incomplete = !leftovers.isEmpty();
                if (incomplete) remainder.add(Delivery.items(leftovers, false));
                if (delivery.experience() != 0) recipient.giveExp(delivery.experience(), false);
                if (!delivery.drop() && !incomplete) {
                    io.github.hello09x.fakeplayer.core.platform.TransferReceiptMarker.markApplied(recipient, delivery.id());
                    newlyApplied.add(delivery.id());
                }
            }
            if (!remainder.isEmpty()) pending.put(recipientId, remainder);
            if (!newlyApplied.isEmpty() || !toDeliver.isEmpty()) recipient.saveData();
        } catch (RuntimeException error) {
            newlyApplied.forEach(id -> io.github.hello09x.fakeplayer.core.platform.TransferReceiptMarker.clear(recipient, id));
            recipient.getInventory().setContents(original);
            recipient.setItemOnCursor(originalCursor);
            io.github.hello09x.devtools.core.utils.ExperienceUtils.clean(recipient);
            recipient.giveExp(originalExp, false);
            try { recipient.saveData(); } catch (RuntimeException rollbackError) { error.addSuppressed(rollbackError); }
            pending.put(recipientId, deliveries);
            throw error;
        }
        // World drops are not part of the player's durable data, so they remain outside the
        // marker protocol until entity-level idempotency can be verified.
        for (int i = 0; i < drops.size(); i++) {
            try { recipient.dropItem(drops.get(i).clone()); }
            catch (RuntimeException error) {
                pending.computeIfAbsent(recipientId, ignored -> new ArrayList<>())
                        .add(new Delivery(drops.subList(i, drops.size()), 0, false, Map.of(), true));
                var acknowledged = new ArrayList<>(alreadyApplied);
                acknowledged.addAll(newlyApplied);
                persistAndAcknowledge(recipient, acknowledged, deliveries.stream().filter(delivery -> !delivery.drop()).toList());
                throw error;
            }
        }
        alreadyApplied.addAll(newlyApplied);
        persistAndAcknowledge(recipient, alreadyApplied, deliveries.stream().filter(delivery -> !delivery.drop()).toList());
    }

    private synchronized void retryPendingPersistence(UUID recipientId, Player recipient) {
        var queued = pending.get(recipientId);
        if (queued == null || queued.stream().map(delivery -> durability.get(delivery.id()))
                .filter(Objects::nonNull).noneMatch(CompletableFuture::isCompletedExceptionally)) return;
        var retry = persist();
        for (var deliveries : pending.values()) for (var delivery : deliveries) durability.put(delivery.id(), retry);
        retry.whenComplete((ignored, error) -> {
            if (error == null) deliverLater(recipient);
        });
    }

    private synchronized CompletableFuture<Void> persistPending() {
        var write = persist();
        for (var deliveries : pending.values()) for (var delivery : deliveries) durability.putIfAbsent(delivery.id(), write);
        return write;
    }

    private void persistAndAcknowledge(Player recipient, List<UUID> receiptIds, List<Delivery> retryOnFailure) {
        var recipientId = recipient.getUniqueId();
        persistPending().whenComplete((ignored, error) -> {
            if (error != null) {
                if (!retryOnFailure.isEmpty()) synchronized (TransferMailbox.this) {
                    pending.computeIfAbsent(recipientId, key -> new ArrayList<>()).addAll(retryOnFailure);
                }
                return;
            }
            if (receiptIds.isEmpty()) return;
            Main.getScheduler().runEntity(recipient, () -> {
                synchronized (TransferMailbox.this) {
                    receiptIds.forEach(id -> io.github.hello09x.fakeplayer.core.platform.TransferReceiptMarker.clear(recipient, id));
                    recipient.saveData();
                }
            }).exceptionally(scheduleError -> {
                Main.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Failed to clear acknowledged transfer markers", scheduleError);
                return null;
            });
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { deliverLater(event.getPlayer()); }

    private static UUID parseReceiptId(String value) {
        if (value != null) try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) {}
        return UUID.randomUUID();
    }

    private synchronized CompletableFuture<Void> persist() {
        var data = new YamlConfiguration();
        pending.forEach((id, deliveries) -> {
            for (int i = 0; i < deliveries.size(); i++) {
                var section = data.createSection(id + "." + i);
                var delivery = deliveries.get(i);
                section.set("id", delivery.id().toString());
                section.set("items", delivery.items()); section.set("experience", delivery.experience()); section.set("cursor", delivery.cursor());
                delivery.slots().forEach((slot, item) -> section.set("slots." + slot, item)); section.set("drop", delivery.drop());
            }
        });
        // Serialize before handing the snapshot to the writer: ItemStacks are never shared with IO.
        var yaml = data.saveToString();
        var write = CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(file.toPath().getParent());
                var temporary = Files.createTempFile(file.toPath().getParent(), "transfers-", ".yml");
                try {
                    Files.writeString(temporary, yaml);
                    try { Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        }, writer).whenComplete((ignored, error) -> {
            if (error != null) Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Transfer mailbox write failed", error);
        });
        return write;
    }

    @Override public void close() {
        CompletableFuture<Void> finalWrite;
        synchronized (this) { finalWrite = persist(); closed = true; writer.shutdown(); }
        try { if (!writer.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Transfer mailbox writer did not finish"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        finalWrite.join();
    }
}
