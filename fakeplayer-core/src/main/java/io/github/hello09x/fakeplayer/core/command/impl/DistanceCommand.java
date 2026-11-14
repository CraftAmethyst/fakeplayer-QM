package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.fakeplayer.core.util.Mth;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Math;

import java.util.Objects;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

@Singleton
public class DistanceCommand extends AbstractCommand {

    /**
     * 查看距离
     */
    public void distance(@NotNull Player sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = super.getFakeplayer(sender, args);
        var to = sender.getLocation().toBlockLocation();
        runForFake(sender, fake, () -> {
        var from = fake.getLocation().toBlockLocation();

        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            reply(sender, translatable(
                    "fakeplayer.command.distance.error.too-far",
                    text(fake.getName(), WHITE)
            ));
            return;
        }

        var euclidean = Mth.floor(from.distance(to), 0.5);
        var x = Math.abs(from.getBlockX() - to.getBlockX());
        var y = Math.abs(from.getBlockY() - to.getBlockY());
        var z = Math.abs(from.getBlockZ() - to.getBlockZ());

        reply(sender, textOfChildren(
                translatable(
                        "fakeplayer.command.distance.title",
                        text(fake.getName(), WHITE)
                ).color(GRAY),
                newline(),
                translatable("fakeplayer.command.distance.euclidean", GRAY), space(), text(euclidean, WHITE), newline(),
                translatable("fakeplayer.command.distance.x", GRAY), space(), text(x, WHITE), newline(),
                translatable("fakeplayer.command.distance.y", GRAY), space(), text(y, WHITE), newline(),
                translatable("fakeplayer.command.distance.z", GRAY), space(), text(z, WHITE)
        ));
        });
    }


}
