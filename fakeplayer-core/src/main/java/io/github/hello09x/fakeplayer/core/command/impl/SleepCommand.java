package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.devtools.core.utils.BlockUtils;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerAutosleepManager;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.translatable;

@Singleton
public class SleepCommand extends AbstractCommand {

    private final FakeplayerAutosleepManager autosleepManager;

    @Inject
    public SleepCommand(FakeplayerAutosleepManager autosleepManager) {
        this.autosleepManager = autosleepManager;
    }

    /**
     * 睡觉
     */
    public void sleep(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = getFakeplayer(sender, args);
        var bed = BlockUtils.getNearbyBlock(fake.getLocation(), 4, block -> {
            if (!(block.getBlockData() instanceof Bed data)) {
                return false;
            }

            return !data.isOccupied() && data.getPart() == Bed.Part.HEAD;
        });
        if (bed == null) {
            return;
        }

        fake.sleep(bed.getLocation(), false);
    }

    /**
     * 睡觉一次
     */
    public void sleepOnce(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        sleep(sender, args);
    }

    /**
     * 自动睡觉
     */
    public void sleepAuto(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = getFakeplayer(sender, args);
        autosleepManager.setAutosleep(fake, true);
        sender.sendMessage(translatable("fakeplayer.command.generic.success"));
    }

    /**
     * 停止自动睡觉
     */
    public void sleepStop(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = getFakeplayer(sender, args);
        autosleepManager.setAutosleep(fake, false);
        sender.sendMessage(translatable("fakeplayer.command.generic.success"));
    }

    /**
     * 起床
     */
    public void wakeup(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var target = getFakeplayer(sender, args);
        if (!target.isSleeping()) {
            return;
        }

        target.wakeup(true);
    }
}
