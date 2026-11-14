package io.github.hello09x.fakeplayer.core.manager.invsee;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.TransferMailbox;
import io.github.hello09x.fakeplayer.core.platform.Escrow;
import io.github.hello09x.fakeplayer.core.platform.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.*;

import static net.kyori.adventure.text.Component.*;

/** The GUI is a view. Only a compare-and-apply operation on the target may change its storage. */
@Singleton
public final class InventoryEditor implements Listener, AutoCloseable {
    private final FakeplayerManager manager;
    private final TransferMailbox mailbox;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    @Inject public InventoryEditor(FakeplayerManager manager, TransferMailbox mailbox) {
        this.manager = manager; this.mailbox = mailbox;
    }

    public CompletableFuture<Boolean> open(Player viewer, Player target) {
        if (closed || viewer.equals(target) || !manager.isFake(target)) return CompletableFuture.completedFuture(false);
        var creator = manager.getCreatorUuid(target);
        if (!viewer.isOp() && !viewer.getUniqueId().equals(creator)) return CompletableFuture.completedFuture(false);
        var session = new Session(viewer, target);
        var targetId = target.getUniqueId();
        while (true) {
            var existing = sessions.putIfAbsent(targetId, session);
            if (existing == null) break;
            if (existing.closed) {
                sessions.remove(targetId, existing);
                continue;
            }
            if (existing.viewer.getUniqueId().equals(viewer.getUniqueId())) {
                return CompletableFuture.completedFuture(true);
            }
            Main.getScheduler().sender(viewer, () -> viewer.sendMessage(translatable("fakeplayer.manager.inventory.busy")));
            return CompletableFuture.completedFuture(false);
        }
        return Main.getScheduler().callEntity(target, () -> {
            if (!target.isOnline()) throw new IllegalStateException("Target offline");
            return copy(target.getInventory().getStorageContents());
        }).thenCompose(contents -> Main.getScheduler().callEntity(viewer, () -> {
            if (closed || session.closed || !viewer.isOnline()) return false;
            var holder = new InvseeInventoryHolder(target.getUniqueId());
            var inventory = Bukkit.createInventory(holder, 36, translatable("fakeplayer.manager.inventory.title", text(target.getName())));
            holder.setInventory(inventory); session.holder = holder;
            session.display = contents; inventory.setContents(copy(contents));
            if (viewer.openInventory(inventory) == null) return false;
            session.refresh = Main.getScheduler().entityTimer(target, session::refresh, 5, 5, session::close);
            return true;
        })).whenComplete((success, error) -> { if (error != null || !Boolean.TRUE.equals(success)) session.close(); });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        var session = find(holder(event.getView().getTopInventory()));
        if (session == null) {
            if (holder(event.getView().getTopInventory()) instanceof InvseeInventoryHolder) event.setCancelled(true);
            return;
        }
        var raw = event.getRawSlot();
        var top = raw >= 0 && raw < 36;
        var cross = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        synchronized (session) {
            if (session.closed || session.pending != null) { event.setCancelled(true); return; }
            if (!top && !cross) return;
            event.setCancelled(true);
            var plan = new Plan(session.display);
            var cursor = copy(event.getCursor());
            var item = top ? copy(plan.after[raw]) : copy(event.getCurrentItem());
            switch (event.getAction()) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> {
                    if (!top || empty(item) || !empty(cursor) && !cursor.isSimilar(item)) return;
                    int wanted = switch (event.getAction()) {
                        case PICKUP_HALF -> (item.getAmount() + 1) / 2;
                        case PICKUP_ONE -> 1;
                        default -> item.getAmount();
                    };
                    int take = Math.min(wanted, item.getMaxStackSize() - amount(cursor));
                    if (take <= 0) return;
                    plan.cursorInput(cursor);
                    plan.after[raw] = withAmount(item, item.getAmount() - take);
                    plan.receipt.add(withAmount(item, amount(cursor) + take)); plan.cursor = true;
                }
                case PLACE_ALL, PLACE_SOME, PLACE_ONE -> {
                    if (!top || empty(cursor) || !empty(item) && !item.isSimilar(cursor)) return;
                    int put = Math.min(cursor.getAmount(), cursor.getMaxStackSize() - amount(item));
                    if (event.getAction() == InventoryAction.PLACE_ONE) put = Math.min(1, put);
                    if (put <= 0) return;
                    plan.cursorInput(cursor);
                    plan.after[raw] = withAmount(cursor, amount(item) + put);
                    plan.receipt.add(withAmount(cursor, cursor.getAmount() - put)); plan.cursor = true;
                }
                case SWAP_WITH_CURSOR -> {
                    if (!top) return;
                    plan.cursorInput(cursor); plan.after[raw] = cursor;
                    plan.receipt.add(item); plan.cursor = true;
                }
                case MOVE_TO_OTHER_INVENTORY -> {
                    if (empty(item)) return;
                    if (top) {
                        var before = copy(session.viewer.getInventory().getStorageContents());
                        var after = copy(before);
                        var remaining = insert(after, item);
                        if (amount(remaining) == amount(item)) return;
                        plan.after[raw] = remaining;
                        for (int slot = 0; slot < before.length; slot++) if (!same(before[slot], after[slot])) {
                            plan.viewerInputs.put(slot, before[slot]); plan.viewerOutputs.put(slot, after[slot]);
                        }
                    }
                    else {
                        plan.viewerInputs.put(event.getSlot(), item);
                        var remaining = insert(plan.after, item);
                        plan.viewerOutputs.put(event.getSlot(), remaining);
                    }
                }
                case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    if (!top) return;
                    int slot = event.getHotbarButton();
                    if (event.getClick() == ClickType.SWAP_OFFHAND) slot = 40;
                    if (slot < 0 || slot > 40) return;
                    var hotbar = copy(session.viewer.getInventory().getItem(slot));
                    plan.viewerInputs.put(slot, hotbar); plan.after[raw] = hotbar;
                    plan.viewerOutputs.put(slot, item);
                }
                case COLLECT_TO_CURSOR -> {
                    if (empty(cursor)) return;
                    int count = cursor.getAmount();
                    for (int slot = 0; slot < 36 && count < cursor.getMaxStackSize(); slot++) {
                        var candidate = plan.after[slot];
                        if (empty(candidate) || !cursor.isSimilar(candidate)) continue;
                        var take = Math.min(candidate.getAmount(), cursor.getMaxStackSize() - count);
                        plan.after[slot] = withAmount(candidate, candidate.getAmount() - take); count += take;
                    }
                    var viewerContents = session.viewer.getInventory().getStorageContents();
                    for (int slot = 0; slot < viewerContents.length && count < cursor.getMaxStackSize(); slot++) {
                        var candidate = copy(viewerContents[slot]);
                        if (empty(candidate) || !cursor.isSimilar(candidate)) continue;
                        var take = Math.min(candidate.getAmount(), cursor.getMaxStackSize() - count);
                        plan.viewerInputs.put(slot, candidate);
                        plan.viewerOutputs.put(slot, withAmount(candidate, candidate.getAmount() - take)); count += take;
                    }
                    plan.cursorInput(cursor); plan.receipt.add(withAmount(cursor, count)); plan.cursor = true;
                }
                case CLONE_STACK -> {
                    if (session.viewer.getGameMode() == org.bukkit.GameMode.CREATIVE) event.setCancelled(false);
                    return;
                }
                case DROP_ALL_SLOT, DROP_ONE_SLOT -> {
                    if (!top || empty(item)) return;
                    var take = event.getAction() == InventoryAction.DROP_ONE_SLOT ? 1 : item.getAmount();
                    plan.after[raw] = withAmount(item, item.getAmount() - take);
                    plan.receipt.add(withAmount(item, take)); plan.drop = true;
                }
                default -> { return; }
            }
            session.submit(plan);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        var session = find(holder(event.getView().getTopInventory()));
        if (session == null) {
            if (holder(event.getView().getTopInventory()) instanceof InvseeInventoryHolder) event.setCancelled(true);
            return;
        }
        synchronized (session) {
            if (session.closed || session.pending != null) { event.setCancelled(true); return; }
            if (event.getRawSlots().stream().noneMatch(slot -> slot < 36)) return;
            event.setCancelled(true);
            var plan = new Plan(session.display); plan.cursorInput(copy(event.getOldCursor()));
            plan.receipt.add(copy(event.getCursor())); plan.cursor = true;
            event.getNewItems().forEach((raw, item) -> {
                if (raw < 36) plan.after[raw] = copy(item);
                else {
                    var slot = event.getView().convertSlot(raw);
                    plan.viewerInputs.put(slot, copy(session.viewer.getInventory().getItem(slot)));
                    plan.viewerOutputs.put(slot, copy(item));
                }
            });
            session.submit(plan);
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        var session = find(holder(event.getInventory())); if (session != null) session.close();
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onQuit(PlayerQuitEvent event) {
        for (var session : sessions.values()) {
            if (session.viewer.getUniqueId().equals(event.getPlayer().getUniqueId()) || session.target.getUniqueId().equals(event.getPlayer().getUniqueId())) session.close();
        }
    }

    private Session find(Object holder) {
        if (!(holder instanceof InvseeInventoryHolder view)) return null;
        var session = sessions.get(view.getTargetId()); return session != null && session.holder == holder ? session : null;
    }

    private static Object holder(org.bukkit.inventory.Inventory inventory) {
        // A real container may be closing during chunk unload. Resolving its holder
        // loads the block again on older CraftBukkit versions; only inspect our GUI.
        return inventory.getLocation() == null ? inventory.getHolder() : null;
    }

    @Override public void close() { closed = true; sessions.values().forEach(Session::close); }

    private final class Session {
        final Player viewer, target;
        volatile InvseeInventoryHolder holder;
        ItemStack[] display;
        TaskHandle refresh;
        volatile boolean closed;
        long revision;
        Escrow<TransferMailbox.Delivery> pending;

        Session(Player viewer, Player target) { this.viewer = viewer; this.target = target; }

        void refresh() {
            long expected;
            synchronized (this) { if (closed || pending != null) return; expected = revision; }
            var contents = copy(target.getInventory().getStorageContents());
            Main.getScheduler().runEntity(viewer, () -> {
                synchronized (this) {
                    if (closed || pending != null || revision != expected) return;
                    display = contents; holder.getInventory().setContents(copy(contents));
                }
            });
        }

        void submit(Plan plan) {
            if (closed || InventoryEditor.this.closed) return;
            // Remove all viewer inputs into escrow before a target task can run.
            var refund = new ArrayList<ItemStack>();
            if (plan.touchesCursor) refund.add(plan.inputCursor);
            refund.addAll(plan.viewerInputs.values());
            var outputs = new ArrayList<>(plan.receipt); outputs.addAll(plan.viewerOutputs.values());
            if (!balance(plan.before, refund).equals(balance(plan.after, outputs))) {
                throw new IllegalStateException("Inventory edit would violate item conservation");
            }
            var cursorRefund = new ArrayList<ItemStack>();
            if (plan.touchesCursor) cursorRefund.add(plan.inputCursor);
            var escrow = new Escrow<>(
                    TransferMailbox.Delivery.inventory(cursorRefund, plan.touchesCursor, plan.viewerInputs, false),
                    TransferMailbox.Delivery.inventory(plan.receipt, plan.cursor, plan.viewerOutputs, plan.drop));
            pending = escrow; revision++;
            plan.viewerInputs.keySet().forEach(slot -> viewer.getInventory().setItem(slot, null));
            if (plan.touchesCursor) viewer.setItemOnCursor(null);
            Main.getScheduler().callEntity(target, () -> escrow.commit(() -> {
                if (!target.isOnline() || !manager.isFake(target)) return false;
                var inventory = target.getInventory();
                var changes = new io.github.hello09x.fakeplayer.core.platform.SlotChanges<>(Arrays.asList(plan.before), Arrays.asList(plan.after), InventoryEditor::same);
                return changes.apply(inventory::getItem, (slot, item) -> inventory.setItem(slot, copy(item)));
            })).whenComplete((success, error) -> {
                synchronized (this) { settle(escrow); }
                // Keep the session locked until the viewer owner has received the acknowledgement.
                Main.getScheduler().runEntity(viewer, () -> {
                    mailbox.deliver(viewer);
                    synchronized (this) {
                        if (pending == escrow) pending = null;
                    }
                }).whenComplete((ignored, deliveryError) -> {
                    if (deliveryError != null) close();
                });
                if (error != null) Main.getInstance().getLogger().log(java.util.logging.Level.WARNING, "Inventory edit failed", error);
            });
        }

        private void settle(Escrow<TransferMailbox.Delivery> escrow) {
            escrow.settleTo(delivery -> mailbox.credit(viewer.getUniqueId(), delivery));
        }

        synchronized void close() {
            if (closed) return;
            closed = true;
            sessions.remove(target.getUniqueId(), this);
            if (pending != null) settle(pending);
            if (refresh != null) refresh.cancel();
        }
    }

    private static final class Plan {
        final ItemStack[] before, after;
        final Map<Integer, ItemStack> viewerInputs = new HashMap<>();
        final List<ItemStack> receipt = new ArrayList<>();
        final Map<Integer, ItemStack> viewerOutputs = new HashMap<>();
        ItemStack inputCursor;
        boolean touchesCursor, cursor, drop;
        Plan(ItemStack[] before) { this.before = copy(before); this.after = copy(before); }
        void cursorInput(ItemStack cursor) { inputCursor = cursor; touchesCursor = true; }
    }

    private static ItemStack insert(ItemStack[] contents, ItemStack input) {
        var left = input.getAmount();
        for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < contents.length && left > 0; slot++) {
            var current = contents[slot];
            if (pass == 0 && (empty(current) || !current.isSimilar(input)) || pass == 1 && !empty(current)) continue;
            var put = Math.min(left, input.getMaxStackSize() - amount(current));
            contents[slot] = withAmount(input, amount(current) + put); left -= put;
        }
        return withAmount(input, left);
    }

    private static boolean empty(ItemStack item) { return item == null || item.getType() == Material.AIR || item.getAmount() <= 0; }
    private static int amount(ItemStack item) { return empty(item) ? 0 : item.getAmount(); }
    private static boolean same(ItemStack a, ItemStack b) { return empty(a) ? empty(b) : a.equals(b); }
    private static ItemStack copy(ItemStack item) { return empty(item) ? null : item.clone(); }
    private static ItemStack withAmount(ItemStack item, int amount) { if (amount <= 0) return null; var copy = item.clone(); copy.setAmount(amount); return copy; }
    private static ItemStack[] copy(ItemStack[] contents) { return Arrays.stream(contents).map(InventoryEditor::copy).toArray(ItemStack[]::new); }

    private static Map<ItemStack, Long> balance(ItemStack[] storage, List<ItemStack> extras) {
        var balance = new HashMap<ItemStack, Long>();
        java.util.stream.Stream.concat(Arrays.stream(storage), extras.stream()).filter(item -> !empty(item)).forEach(item -> {
            var key = item.clone(); key.setAmount(1);
            balance.merge(key, (long) item.getAmount(), Long::sum);
        });
        return balance;
    }
}
