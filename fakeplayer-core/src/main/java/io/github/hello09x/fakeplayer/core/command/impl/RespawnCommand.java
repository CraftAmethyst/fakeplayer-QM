package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.translatable;

@Singleton
public class RespawnCommand extends AbstractCommand {

    /**
     * 重生
     */
    public void respawn(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = super.getFakeplayer(sender, args, manager::isDeadSnapshot);
        io.github.hello09x.fakeplayer.core.Main.getScheduler().callEntity(fake, () -> {
            if (!fake.isDead()) throw new IllegalStateException("Fakeplayer is alive");
            return bridge.fromPlayer(fake).respawnAsync();
        }).thenCompose(result -> result).whenComplete((ignored, error) -> reply(sender, translatable(
                error == null ? "fakeplayer.command.generic.success" : "fakeplayer.command.generic.error.operation-failed")));
    }

}
