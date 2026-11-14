package io.github.hello09x.fakeplayer.core.entity;

import io.github.hello09x.fakeplayer.api.spi.NMSServerPlayer;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.util.FakeplayerFeatureUtils;
import org.bukkit.Location;
import io.github.hello09x.fakeplayer.core.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

public class FakeplayerTicker implements Runnable {

    private TaskHandle task;

    public void start() {
        if (task != null) throw new IllegalStateException("Ticker already started");
        task = Main.getScheduler().entityTimer(player.getPlayer(), this, 1, 1);
    }

    public void cancel() {
        if (task != null) task.cancel();
    }

    public final static long NON_REMOVE_AT = -1;

    @NotNull
    private final Fakeplayer player;

    /**
     * 移除时间
     * <p>如果不需要定时移除则为 0</p>
     */
    private final long removeAt;

    /**
     * 是否是第一次 tick
     */
    private boolean firstTick;

    public FakeplayerTicker(
            @NotNull Fakeplayer player,
            long lifespan
    ) {
        this.player = player;
        this.removeAt = lifespan > 0 ? System.currentTimeMillis() + lifespan : NON_REMOVE_AT;
        this.firstTick = true;
    }

    @Override
    public void run() {
        if (player.getState() == Fakeplayer.State.MIGRATING) return;
        if (!player.isOnline()) {
            cancel();
            return;
        }

        if (this.removeAt != NON_REMOVE_AT && this.player.getTickCount() % 20 == 0 && System.currentTimeMillis() > removeAt) {
            Main.getInjector().getInstance(FakeplayerManager.class).remove(player.getName(), "lifespan ends");
            cancel();
            return;
        }

        // 真实的玩家是通过 ServerGamePacketListenerImpl#tick() 进行时刻运算的
        // 这个方法会修复第一次 tick 坐标错误的问题
        // 但是这个方法会导致强制修正坐标为客户端坐标, 然而假人的连接并不会发送任何坐标
        // 因此这里自行修复第一次 tick 的坐标, 并直接调用 ServerPlayer#doTick() 来进行时刻运算
        if (this.firstTick) {
            this.doFirstTick();
        } else {
            this.doTick();
        }
        Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.action.ActionManager.class).tick(player.getPlayer());
        Main.getInjector().getInstance(FakeplayerManager.class).publishDead(player.getPlayer());
        Main.getInjector().getInstance(io.github.hello09x.fakeplayer.core.manager.PersistentFakeplayerManager.class).capture(player.getPlayer());
    }

    /**
     * 处理第一次 tick
     * <p>在这里在 {@link NMSServerPlayer#doTick()} 之后, 强行设置一次坐标解决被其他插件干预导致随机传送</p>
     * <p>似乎是 clearfog 或者 multiverse 插件导致的</p>
     */
    private void doFirstTick() {
        if (Main.getScheduler().isFolia()) {
            this.doTick();
            this.firstTick = false;
            return;
        }
        var handle = this.player.getHandle();
        var player = this.player.getPlayer();
        var x = handle.getX();
        var y = handle.getY();
        var z = handle.getZ();

        // 将本 tick 的移动取消
        handle.setXo(x);
        handle.setYo(y);
        handle.setZo(z);

        FakeplayerFeatureUtils.clearChangingDimension(player);
        handle.doTick();

        // clearFog 插件会在第一次传送的时候改变了玩家的位置, 因此必须进行一次传送
        // A restored vehicle is already on the final spawn location. Teleporting
        // its passenger here would immediately undo attachment restoration.
        if (!player.isInsideVehicle()) {
            player.teleport(new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch()));
        }
        handle.absMoveTo(x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
        this.firstTick = false;
    }

    private void doTick() {
        var handle = this.player.getHandle();
        FakeplayerFeatureUtils.clearChangingDimension(this.player.getPlayer());
        handle.doTick();
    }

}
