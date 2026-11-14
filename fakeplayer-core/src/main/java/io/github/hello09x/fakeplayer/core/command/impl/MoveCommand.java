package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Singleton;
import dev.jorel.commandapi.executors.CommandExecutor;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.platform.TaskHandle;
import org.jetbrains.annotations.Range;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MoveCommand extends AbstractCommand {
    private final Map<UUID, TaskHandle> stopTasks = new ConcurrentHashMap<>();

    public CommandExecutor move(@Range(from = 0, to = 1) float forward, @Range(from = 0, to = 1) float strafing) {
        return (sender, args) -> {
            var fake = getFakeplayer(sender, args);
            runForFakeWithSuccess(sender, fake, () -> {
                var handle = bridge.fromPlayer(fake);
                float velocity = fake.isSneaking() ? 0.3F : 1.0F;
                if (forward != 0) handle.setZza(velocity * forward);
                if (strafing != 0) handle.setXxa(velocity * strafing);
                var previous = stopTasks.remove(fake.getUniqueId());
                if (previous != null) previous.cancel();
                stopTasks.put(fake.getUniqueId(), Main.getScheduler().entityLater(fake, () -> {
                    handle.setXxa(0);
                    handle.setZza(0);
                    stopTasks.remove(fake.getUniqueId());
                }, fake.isSprinting() ? 40 : 20));
            });
        };
    }
}
