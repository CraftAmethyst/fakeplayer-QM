package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerAutosleepManager;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

@Singleton
public class SleepCommand extends AbstractCommand {

    private final FakeplayerAutosleepManager autosleepManager;

    @Inject
    public SleepCommand(FakeplayerAutosleepManager autosleepManager) {
        this.autosleepManager = autosleepManager;
    }

    public void sleep(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = getFakeplayer(sender, args);
        if (!autosleepManager.trySleep(fake, false)) {
            sender.sendMessage(translatable(
                    "fakeplayer.command.sleep.error.no-bed",
                    text(fake.getName(), WHITE)
            ).color(GRAY));
        }
    }

    public void wakeup(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var target = getFakeplayer(sender, args);
        if (!target.isSleeping()) {
            return;
        }

        target.wakeup(true);
    }
}
