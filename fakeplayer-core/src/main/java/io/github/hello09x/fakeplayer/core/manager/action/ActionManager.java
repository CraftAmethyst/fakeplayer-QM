package io.github.hello09x.fakeplayer.core.manager.action;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.api.spi.*;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.platform.TaskHandle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Singleton
public class ActionManager {
    private final Map<UUID, PlayerActions> managers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ActionType>> snapshots = new ConcurrentHashMap<>();
    private final NMSBridge bridge;

    @Inject
    public ActionManager(NMSBridge bridge) { this.bridge = bridge; }

    public boolean hasActiveAction(@NotNull Player player, @NotNull ActionType action) {
        return getActiveActions(player).contains(action);
    }

    public @NotNull @Unmodifiable Set<ActionType> getActiveActions(@NotNull Player player) {
        return snapshots.getOrDefault(player.getUniqueId(), Set.of());
    }

    public void setAction(@NotNull Player player, @NotNull ActionType action, @NotNull ActionSetting setting) {
        Main.getScheduler().runEntity(player, () -> {
            var state = managers.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerActions(player));
            var previous = state.actions.remove(action);
            if (previous != null) previous.stop();
            state.actions.put(action, bridge.createAction(player, action, setting));
            state.publish();

        });
    }

    public void stop(@NotNull Player player) {
        Main.getScheduler().runEntity(player, () -> stopOwned(player));
    }

    /** Called during native quit on the owner, even after plugin schedulers close. */
    public void stopOwned(Player player) {
        var state = managers.get(player.getUniqueId());
        if (state != null) state.stop();
    }

    public void tick(Player player) {
        var state = managers.get(player.getUniqueId());
        if (state != null) state.tick();
    }

    /** Legacy entry point. Each player is dispatched to its owning region. */
    public void tick() {
        managers.values().forEach(state -> Main.getScheduler().runEntity(state.player, state::tick));
    }

    private void discard(UUID uuid, PlayerActions state) {
        if (managers.remove(uuid, state)) snapshots.remove(uuid);
    }

    private final class PlayerActions {
        private final Player player;
        private final Map<ActionType, ActionTicker> actions = new EnumMap<>(ActionType.class);
        private TaskHandle task;

        private PlayerActions(Player player) { this.player = player; }

        private void publish() {
            var active = EnumSet.noneOf(ActionType.class);
            actions.forEach((type, ticker) -> { if (ticker.getSetting().remains != 0) active.add(type); });
            snapshots.put(player.getUniqueId(), Set.copyOf(active));
        }

        private void tick() {
            if (!player.isOnline() || !player.isValid()) { stop(); return; }
            // Tickers may re-enter the manager. Iterate a snapshot and remove by identity.
            for (var entry : new EnumMap<>(actions).entrySet()) {
                if (actions.get(entry.getKey()) != entry.getValue()) continue;
                try {
                    if (entry.getValue().tick()) actions.remove(entry.getKey(), entry.getValue());
                } catch (Throwable exception) {
                    Main.getInstance().getLogger().log(Level.WARNING, "Fakeplayer action failed", exception);
                }
            }
            publish();
            if (actions.isEmpty()) stop();
        }

        private void stop() {
            var stopped = new ArrayList<>(actions.values());
            actions.clear();
            discard(player.getUniqueId(), this);
            if (task != null) task.cancel();
            stopped.forEach(ActionTicker::stop);
        }
    }
}
