package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

@Singleton
public class KillCommand extends AbstractCommand {

    /**
     * 移除假人
     */
    public void kill(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fakes = super.getFakeplayers(sender, args);

        if (fakes.isEmpty()) {
            reply(sender, translatable("fakeplayer.command.kill.error.non-removed", GRAY));
            return;
        }

        var operations = fakes.stream().map(fake -> manager.removeAsync(fake.getName(), text("command kill"))
                .thenApply(removed -> removed ? fake.getName() : null)).toList();
        java.util.concurrent.CompletableFuture.allOf(operations.toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        reply(sender, translatable("fakeplayer.command.generic.error.operation-failed"));
                        return;
                    }
                    var names = new StringJoiner(", ");
                    operations.forEach(operation -> { var name = operation.getNow(null); if (name != null) names.add(name); });
                    reply(sender, textOfChildren(translatable("fakeplayer.command.kill.success.removed", GRAY), space(), text(names.toString())));
                });
    }


}
