package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.command.Permission;
import io.github.hello09x.fakeplayer.core.manager.feature.FakeplayerFeatureManager;
import io.github.hello09x.fakeplayer.core.repository.model.Feature;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Singleton
public final class FakeplayerMenu implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 36;
    private static final int[] FEATURE_SLOTS = {10, 13, 16, 19, 22, 25, 28, 31, 34};

    private final FakeplayerManager manager;
    private final FakeplayerFeatureManager featureManager;

    @Inject
    public FakeplayerMenu(FakeplayerManager manager, FakeplayerFeatureManager featureManager) {
        this.manager = manager;
        this.featureManager = featureManager;
    }

    public void openList(@NotNull Player viewer, int page) {
        Main.getScheduler().runEntity(viewer, () -> renderList(viewer, page));
    }

    private void renderList(Player viewer, int requestedPage) {
        var players = (viewer.isOp() ? manager.getAll() : manager.getAll(viewer)).stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        var pageCount = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        var page = Math.max(1, Math.min(requestedPage, pageCount));
        var holder = new MenuHolder(MenuType.LIST, page, null);
        var inventory = createInventory(holder, translatable("fakeplayer.gui.list.title"), viewer);
        holder.inventory = inventory;
        inventory.setItem(4, item(viewer, Material.BOOK,
                translatable("fakeplayer.gui.list.count", text(players.size())), List.of()));

        var start = (page - 1) * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, players.size()); i++) {
            var fake = players.get(i);
            var slot = 9 + i - start;
            var creator = manager.getCreatorName(fake);
            var location = manager.getLocationSnapshot(fake);
            var lore = new ArrayList<Component>();
            lore.add(translatable("fakeplayer.gui.list.creator", text(creator == null ? "-" : creator, WHITE)));
            lore.add(translatable("fakeplayer.gui.list.location", location == null
                    ? translatable("fakeplayer.gui.list.unknown-location")
                    : text(location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(), WHITE)));
            lore.add(Component.empty());
            lore.add(translatable("fakeplayer.gui.list.open", AQUA));
            inventory.setItem(slot, item(viewer, Material.PLAYER_HEAD, text(fake.getName(), GOLD), lore));
            holder.players.put(slot, fake.getUniqueId());
        }

        if (players.isEmpty()) {
            inventory.setItem(22, item(viewer, Material.BARRIER, translatable("fakeplayer.gui.list.empty", RED), List.of()));
        }

        if (page > 1) {
            inventory.setItem(45, item(viewer, Material.ARROW, translatable("fakeplayer.gui.list.previous", WHITE), List.of()));
            holder.actions.put(45, MenuAction.PREVIOUS);
        }
        inventory.setItem(47, item(viewer, Material.COMPASS, translatable("fakeplayer.gui.list.refresh", WHITE), List.of()));
        holder.actions.put(47, MenuAction.REFRESH);
        inventory.setItem(49, item(viewer, Material.PAPER,
                translatable("fakeplayer.gui.list.page", text(page), text(pageCount)), List.of()));
        if (viewer.hasPermission(Permission.spawn)) {
            inventory.setItem(51, item(viewer, Material.CHEST, translatable("fakeplayer.gui.list.spawn", WHITE), List.of()));
            holder.actions.put(51, MenuAction.SPAWN);
        }
        if (page < pageCount) {
            inventory.setItem(53, item(viewer, Material.ARROW, translatable("fakeplayer.gui.list.next", WHITE), List.of()));
            holder.actions.put(53, MenuAction.NEXT);
        }
        viewer.openInventory(inventory);
    }

    private void openSettings(Player viewer, UUID fakeId, int page) {
        var fake = findFake(viewer, fakeId);
        if (fake == null) {
            viewer.sendMessage(translatable("fakeplayer.command.generic.error.non-fake-player", RED));
            renderList(viewer, page);
            return;
        }

        var valuesFuture = Main.getScheduler().callEntity(fake, () -> {
            var values = new EnumMap<Feature, String>(Feature.class);
            for (var feature : Feature.values()) {
                var detector = feature.getDetector();
                if (detector != null) values.put(feature, detector.apply(fake));
            }
            return values;
        });
        var defaultsFuture = featureManager.getFeaturesAsync(viewer);
        valuesFuture.thenCombine(defaultsFuture, (values, defaults) -> {
            values.put(Feature.skin, defaults.get(Feature.skin).value());
            return values;
        }).whenComplete((values, error) -> Main.getScheduler().runEntity(viewer, () -> {
            var currentFake = findFake(viewer, fakeId);
            if (currentFake == null) {
                viewer.sendMessage(translatable("fakeplayer.command.generic.error.non-fake-player", RED));
                renderList(viewer, page);
                return;
            }
            if (error != null) {
                viewer.sendMessage(translatable("fakeplayer.command.generic.error.operation-failed", RED));
                return;
            }
            renderSettings(viewer, currentFake, page, values);
        }));
    }

    private void renderSettings(Player viewer, Player fake, int page, Map<Feature, String> values) {
        var holder = new MenuHolder(MenuType.SETTINGS, page, fake.getUniqueId());
        var inventory = createInventory(holder, translatable("fakeplayer.gui.settings.title", text(fake.getName())), viewer);
        holder.inventory = inventory;

        var location = manager.getLocationSnapshot(fake);
        List<Component> headerLore = new ArrayList<>();
        if (location != null) {
            headerLore.add(translatable("fakeplayer.gui.list.location", text(location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(), WHITE)));
        }
        inventory.setItem(4, item(viewer, Material.PLAYER_HEAD, translatable("fakeplayer.gui.settings.header", text(fake.getName(), GOLD)), headerLore));

        var features = Feature.values();
        for (int i = 0; i < features.length; i++) {
            var feature = features[i];
            var value = values.get(feature);
            var allowed = feature.testPermissions(viewer);
            var skinDefault = feature == Feature.skin;
            var editable = allowed && (skinDefault ? viewer.hasPermission(Permission.config) : viewer.hasPermission(Permission.set));
            var lore = new ArrayList<Component>();
            lore.add(translatable("fakeplayer.gui.settings.current", displayOption(value)));
            if (skinDefault) lore.add(translatable("fakeplayer.gui.settings.skin-note", GRAY));
            if (!editable) {
                lore.add(translatable("fakeplayer.gui.settings.no-permission", RED));
            } else if (!skinDefault && (!feature.hasDetector() || !feature.hasModifier())) {
                lore.add(translatable("fakeplayer.gui.settings.unavailable", GRAY));
            } else {
                var next = nextOption(feature, value);
                lore.add(translatable("fakeplayer.gui.settings.switch", AQUA, displayOption(next)));
                holder.features.put(FEATURE_SLOTS[i], feature);
            }
            inventory.setItem(FEATURE_SLOTS[i], item(viewer, featureMaterial(feature),
                    translatable(feature.translationKey(), editable ? WHITE : DARK_GRAY), lore));
        }

        inventory.setItem(45, item(viewer, Material.ARROW, translatable("fakeplayer.gui.settings.back", WHITE), List.of()));
        holder.actions.put(45, MenuAction.BACK);
        if (viewer.hasPermission(Permission.invsee)) {
            inventory.setItem(47, item(viewer, Material.CHEST, translatable("fakeplayer.gui.settings.inventory", WHITE), List.of()));
            holder.actions.put(47, MenuAction.INVENTORY);
        }
        if (viewer.hasPermission(Permission.status)) {
            inventory.setItem(49, item(viewer, Material.BOOK, translatable("fakeplayer.gui.settings.status", WHITE), List.of()));
            holder.actions.put(49, MenuAction.STATUS);
        }
        if (viewer.hasPermission(Permission.tp)) {
            inventory.setItem(51, item(viewer, Material.ENDER_PEARL, translatable("fakeplayer.gui.settings.teleport", WHITE), List.of()));
            holder.actions.put(51, MenuAction.TELEPORT);
        }
        if (viewer.hasPermission(Permission.kill)) {
            inventory.setItem(53, item(viewer, Material.TNT, translatable("fakeplayer.gui.settings.remove", RED), List.of()));
            holder.actions.put(53, MenuAction.REMOVE);
        }
        viewer.openInventory(inventory);
    }

    private void openConfirmation(Player viewer, MenuHolder previous) {
        var fake = findFake(viewer, previous.targetId);
        if (fake == null) {
            renderList(viewer, previous.page);
            return;
        }
        var holder = new MenuHolder(MenuType.CONFIRM, previous.page, fake.getUniqueId());
        var inventory = createInventory(holder, translatable("fakeplayer.gui.confirm.title", text(fake.getName())), viewer);
        holder.inventory = inventory;
        inventory.setItem(11, item(viewer, Material.LIME_CONCRETE, translatable("fakeplayer.gui.confirm.accept", GREEN), List.of()));
        inventory.setItem(13, item(viewer, Material.PLAYER_HEAD, text(fake.getName(), GOLD),
                List.of(translatable("fakeplayer.gui.confirm.description", RED))));
        inventory.setItem(15, item(viewer, Material.RED_CONCRETE, translatable("fakeplayer.gui.confirm.cancel", WHITE), List.of()));
        holder.actions.put(11, MenuAction.CONFIRM_REMOVE);
        holder.actions.put(15, MenuAction.CANCEL_REMOVE);
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || event.getClickedInventory() != event.getView().getTopInventory()) return;

        var slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) return;
        if (holder.type == MenuType.LIST) {
            var target = holder.players.get(slot);
            if (target != null) {
                openSettings(viewer, target, holder.page);
                return;
            }
        } else if (holder.type == MenuType.SETTINGS) {
            var feature = holder.features.get(slot);
            if (feature != null) {
                changeFeature(viewer, holder.targetId, holder.page, feature);
                return;
            }
        }

        var action = holder.actions.get(slot);
        if (action != null) handleAction(viewer, holder, action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private void handleAction(Player viewer, MenuHolder holder, MenuAction action) {
        switch (action) {
            case PREVIOUS -> renderList(viewer, holder.page - 1);
            case NEXT -> renderList(viewer, holder.page + 1);
            case REFRESH -> renderList(viewer, holder.page);
            case SPAWN -> viewer.performCommand("fp spawn");
            case BACK -> renderList(viewer, holder.page);
            case CANCEL_REMOVE -> openSettings(viewer, holder.targetId, holder.page);
            case INVENTORY -> runTargetCommand(viewer, holder.targetId, Permission.invsee, "invsee");
            case STATUS -> runTargetCommand(viewer, holder.targetId, Permission.status, "status");
            case TELEPORT -> runTargetCommand(viewer, holder.targetId, Permission.tp, "tp");
            case REMOVE -> openConfirmation(viewer, holder);
            case CONFIRM_REMOVE -> removeFake(viewer, holder);
        }
    }

    private void runTargetCommand(Player viewer, UUID targetId, String permission, String command) {
        if (!viewer.hasPermission(permission)) return;
        var fake = findFake(viewer, targetId);
        if (fake == null) {
            renderList(viewer, 1);
            return;
        }
        viewer.performCommand("fp " + command + " " + fake.getName());
    }

    private void removeFake(Player viewer, MenuHolder holder) {
        if (!viewer.hasPermission(Permission.kill)) return;
        var fake = findFake(viewer, holder.targetId);
        if (fake == null) {
            renderList(viewer, holder.page);
            return;
        }
        var name = fake.getName();
        manager.removeAsync(name, text("removed from menu")).whenComplete((removed, error) -> Main.getScheduler().runEntity(viewer, () -> {
            if (error != null || !Boolean.TRUE.equals(removed)) {
                viewer.sendMessage(translatable("fakeplayer.command.generic.error.operation-failed", RED));
                renderList(viewer, holder.page);
                return;
            }
            viewer.sendMessage(translatable("fakeplayer.command.generic.success", GREEN));
            renderList(viewer, holder.page);
        }));
    }

    private void changeFeature(Player viewer, UUID targetId, int page, Feature feature) {
        if (!feature.testPermissions(viewer)) return;
        var isDefault = feature == Feature.skin;
        var requiredPermission = isDefault ? Permission.config : Permission.set;
        if (!viewer.hasPermission(requiredPermission)) return;

        if (isDefault) {
            featureManager.getFeaturesAsync(viewer).thenCompose(values -> {
                var option = nextOption(feature, values.get(feature).value());
                return Main.getScheduler().async(() -> {
                    featureManager.setFeature(viewer, feature, option);
                    return option;
                });
            }).whenComplete((option, error) -> Main.getScheduler().runEntity(viewer, () -> {
                if (error != null) {
                    viewer.sendMessage(translatable("fakeplayer.command.generic.error.operation-failed", RED));
                    return;
                }
                viewer.sendMessage(translatable("fakeplayer.command.config.set.success",
                        translatable(feature.translationKey(), GOLD), displayOption(option)));
                refreshSettingsIfOpen(viewer, targetId, page);
            }));
            return;
        }

        var fake = findFake(viewer, targetId);
        if (fake == null || !feature.hasDetector() || !feature.hasModifier()) return;
        Main.getScheduler().callEntity(fake, () -> {
            var option = nextOption(feature, feature.getDetector().apply(fake));
            feature.getModifier().accept(fake, option);
            return option;
        }).whenComplete((option, error) -> Main.getScheduler().runEntity(viewer, () -> {
            if (error != null) {
                viewer.sendMessage(translatable("fakeplayer.command.generic.error.operation-failed", RED));
                return;
            }
            viewer.sendMessage(translatable("fakeplayer.command.set.success",
                    text(fake.getName(), WHITE), translatable(feature.translationKey(), GOLD), displayOption(option)));
            refreshSettingsIfOpen(viewer, targetId, page);
        }));
    }

    private void refreshSettingsIfOpen(Player viewer, UUID targetId, int page) {
        if (!(viewer.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder current)
                || current.type != MenuType.SETTINGS || !targetId.equals(current.targetId)) return;
        openSettings(viewer, targetId, page);
    }

    private Player findFake(Player viewer, UUID id) {
        if (id == null) return null;
        var players = viewer.isOp() ? manager.getAll() : manager.getAll(viewer);
        return players.stream().filter(fake -> fake.getUniqueId().equals(id)).findFirst().orElse(null);
    }

    private static String nextOption(Feature feature, String current) {
        var options = feature.getOptions();
        var index = options.indexOf(current);
        return options.get((index + 1 + options.size()) % options.size());
    }

    private static Component displayOption(String value) {
        if ("true".equalsIgnoreCase(value)) return translatable("fakeplayer.gui.settings.enabled", GREEN);
        if ("false".equalsIgnoreCase(value)) return translatable("fakeplayer.gui.settings.disabled", RED);
        return text(value == null ? "-" : value, WHITE);
    }

    private static Material featureMaterial(Feature feature) {
        return switch (feature) {
            case collidable -> Material.IRON_CHESTPLATE;
            case invulnerable -> Material.TOTEM_OF_UNDYING;
            case wolverine -> Material.GOLDEN_APPLE;
            case look_at_entity -> Material.SPYGLASS;
            case pickup_items -> Material.HOPPER;
            case skin -> Material.PLAYER_HEAD;
            case replenish -> Material.DISPENSER;
            case autofish -> Material.FISHING_ROD;
            case autosleep -> Material.RED_BED;
        };
    }

    private static Inventory createInventory(MenuHolder holder, Component title, Player viewer) {
        var localizedTitle = GlobalTranslator.render(title, viewer.locale());
        return Bukkit.createInventory(holder, holder.type == MenuType.CONFIRM ? 27 : INVENTORY_SIZE, localizedTitle);
    }

    private static ItemStack item(Player viewer, Material material, Component name, List<Component> lore) {
        var item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(GlobalTranslator.render(name, viewer.locale()));
        if (!lore.isEmpty()) meta.lore(lore.stream()
                .map(line -> GlobalTranslator.render(line, viewer.locale()))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private enum MenuType { LIST, SETTINGS, CONFIRM }

    private enum MenuAction {
        PREVIOUS, NEXT, REFRESH, SPAWN, BACK, INVENTORY, STATUS, TELEPORT, REMOVE, CONFIRM_REMOVE, CANCEL_REMOVE
    }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final int page;
        private final UUID targetId;
        private final Map<Integer, UUID> players = new HashMap<>();
        private final Map<Integer, Feature> features = new HashMap<>();
        private final Map<Integer, MenuAction> actions = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(MenuType type, int page, UUID targetId) {
            this.type = type;
            this.page = page;
            this.targetId = targetId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
