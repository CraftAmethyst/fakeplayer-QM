package io.github.hello09x.fakeplayer.core.manager.invsee;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * @author tanyaofei
 * @since 2024/8/12
 **/
public interface InvseeManager extends Listener {

    default java.util.concurrent.CompletableFuture<Boolean> invseeAsync(Player viewer, Player whom) {
        return java.util.concurrent.CompletableFuture.completedFuture(invsee(viewer, whom));
    }

    boolean invsee(@NotNull Player viewer, @NotNull Player whom);

}
