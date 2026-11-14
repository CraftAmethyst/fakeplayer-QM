package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerSkinManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

@Singleton
public class SkinCommand extends AbstractCommand {
    private final Map<String, Long> requests = new ConcurrentHashMap<>();
    private final FakeplayerSkinManager manager;

    @Inject
    public SkinCommand(FakeplayerSkinManager manager) { this.manager = manager; }

    public void skin(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fake = getFakeplayer(sender, args);
        var player = Objects.requireNonNull((OfflinePlayer) args.get("player"));
        var now = System.nanoTime();
        requests.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (!sender.isOp() && requests.putIfAbsent(sender.getName(), now + java.util.concurrent.TimeUnit.SECONDS.toNanos(60)) != null) {
            reply(sender, translatable("fakeplayer.command.skin.error.too-many-operations", RED));
            return;
        }
        manager.useOnlineSkinAsync(fake, player).thenCompose(success -> {
            if (!success) return java.util.concurrent.CompletableFuture.completedFuture(false);
            return manager.rememberSkinAsync(sender, fake, player).thenApply(ignored -> true);
        }).whenComplete((success, error) -> reply(sender, translatable(
                error == null && Boolean.TRUE.equals(success) ? "fakeplayer.command.generic.success" : "fakeplayer.command.generic.error.operation-failed")));
    }
}
