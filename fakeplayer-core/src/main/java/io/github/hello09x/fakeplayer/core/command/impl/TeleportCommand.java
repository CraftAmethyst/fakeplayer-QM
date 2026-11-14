package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.platform.Teleports;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.CompletableFuture;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

@Singleton
public class TeleportCommand extends AbstractCommand {
    public void tp(@NotNull Player sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = getFakeplayer(sender, args);
        report(sender, Main.getScheduler().callEntity(fake, fake::getLocation).thenCompose(location -> Teleports.to(sender, location)));
    }

    public void tphere(@NotNull Player sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        report(sender, Teleports.to(getFakeplayer(sender, args), sender.getLocation()));
    }

    public void tps(@NotNull Player sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        report(sender, Teleports.swap(sender, getFakeplayer(sender, args)));
    }

    private void report(Player sender, CompletableFuture<Boolean> result) {
        result.whenComplete((success, error) -> {
            if (error != null) log.log(java.util.logging.Level.WARNING, "Teleport failed", error);
            if (error == null && Boolean.TRUE.equals(success)) {
                reply(sender, translatable("fakeplayer.command.generic.success"));
            } else {
                reply(sender, translatable("fakeplayer.command.teleport.error.canceled", RED));
            }
        });
    }
}
