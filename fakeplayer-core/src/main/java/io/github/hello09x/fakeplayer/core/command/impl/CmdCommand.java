package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Singleton;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.command.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

@Singleton
public class CmdCommand extends AbstractCommand {

    private final Logger log = Main.getInstance().getLogger();

    private static @NotNull String stringifyCommand(@NotNull Command command, @NotNull String[] args) {
        var builder = new StringBuilder("/").append(command.getName());
        if (args.length > 0) {
            builder.append(" ").append(String.join(" ", args));
        }
        return builder.toString();
    }

    /**
     * 假人执行命令
     */
    public void cmd(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = super.getFakeplayer(sender, args);
        var input = Objects.requireNonNull((String) args.get("command")).trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }

        if (input.isBlank()) {
            throw CommandAPI.failWithString("Unknown command");
        }

        var rawArgs = input.split("\\s+");
        var commandLabel = rawArgs[0];
        var command = Bukkit.getCommandMap().getCommand(commandLabel);
        if (command == null) {
            throw CommandAPI.failWithString("Unknown command");
        }

        var name = command.getName();
        if (!sender.hasPermission(Permission.cmd) && !config.getAllowCommands().contains(name)) {
            sender.sendMessage(translatable("fakeplayer.command.cmd.error.no-permission", RED));
            return;
        }

        if (!sender.isOp() && (name.equals("fakeplayer") || name.equals("fp"))) {
            sender.sendMessage(translatable("fakeplayer.command.cmd.error.no-permission", RED));
            return;
        }

        if (!command.testPermission(fake)) {
            sender.sendMessage(translatable("fakeplayer.command.cmd.error.fakeplayer-has-no-permission", text(fake.getName())).color(RED));
            return;
        }

        var commandArgs = Arrays.copyOfRange(rawArgs, 1, rawArgs.length);
        if (!command.execute(fake, commandLabel, commandArgs)) {
            sender.sendMessage(translatable("fakeplayer.command.cmd.error.execute-failed", RED));
            return;
        }

        sender.sendMessage(translatable(
                "fakeplayer.command.generic.success",
                GRAY
        ));

        log.info("%s issued server command: %s".formatted(fake.getName(), stringifyCommand(command, commandArgs)));
    }

}
