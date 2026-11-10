package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.devtools.core.translation.TranslatorUtils;
import io.github.hello09x.fakeplayer.api.spi.ActionSetting;
import io.github.hello09x.fakeplayer.api.spi.ActionType;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.translation.FakeplayerTranslator;
import io.github.hello09x.fakeplayer.core.util.FakeplayerFeatureUtils;
import io.github.hello09x.fakeplayer.core.util.VersionUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Singleton
public class TestCommand extends AbstractCommand {

    private static final long TEST_PLAYER_LIFESPAN_MILLIS = 30_000L;

    private final FakeplayerTranslator translator;
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    public TestCommand(FakeplayerTranslator translator) {
        this.translator = translator;
    }

    public void test(@NotNull CommandSender sender, @NotNull CommandArguments args) {
        if (!running.compareAndSet(false, true)) {
            send(sender, YELLOW, "A self-test is already running.");
            return;
        }

        TestFixture fixture;
        try {
            fixture = TestFixture.create(sender);
        } catch (Throwable throwable) {
            send(sender, RED, "FAIL: World fixture setup - " + describe(throwable));
            log.log(Level.WARNING, "Fakeplayer self-test failed while preparing its world fixture", throwable);
            running.set(false);
            return;
        }
        var name = "FPTest" + Long.toString(System.currentTimeMillis(), 36).substring(2);
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        var initialCount = manager.getSize();
        var testName = name;

        send(sender, AQUA, "Starting compatibility self-test on Minecraft "
                + VersionUtils.getMinecraftVersion() + " with " + bridge.getClass().getSimpleName() + ".");
        send(sender, GRAY, "Temporary player: " + testName);

        try {
            manager.spawnAsync(sender, testName, fixture.spawnLocation(), TEST_PLAYER_LIFESPAN_MILLIS)
                    .whenComplete((fake, throwable) -> Bukkit.getScheduler().runTask(
                            Main.getInstance(),
                            () -> {
                                if (throwable != null) {
                                    failSpawn(sender, testName, fixture, throwable);
                                    return;
                                }
                                runChecks(sender, fake, testName, initialCount, fixture);
                            }
                    ));
        } catch (Throwable throwable) {
            failSpawn(sender, testName, fixture, throwable);
        }
    }

    private void runChecks(CommandSender sender, Player fake, String name, int initialCount, TestFixture fixture) {
        var result = new TestResult();

        check(sender, result, "Adventure translations", () -> {
            translator.reload();
            var translation = translator.translate(
                    "fakeplayer.command.generic.success",
                    TranslatorUtils.getLocale(sender)
            );
            require(translation != null, "test translation was not found");
        });

        check(sender, result, "NMS bridge selection", () ->
                require(bridge.isSupported(), "selected bridge does not support this server"));

        check(sender, result, "Fake player registration", () -> {
            require(manager.getSize() == initialCount + 1, "manager size did not increase");
            require(manager.isFake(fake), "player is not registered as fake");
            require(manager.get(name) == fake, "name lookup returned another player");
        });

        check(sender, result, "Bukkit player lifecycle", () -> {
            require(fake.isOnline(), "temporary player is offline");
            require(fake.isValid(), "temporary player is invalid");
            require(Bukkit.getPlayer(fake.getUniqueId()) == fake, "Bukkit lookup returned another player");
        });

        var handle = bridge.fromPlayer(fake);
        check(sender, result, "NMS player wrapper", () -> {
            require(handle.getPlayer() == fake, "wrapper returned another player");
            var current = fake.getLocation();
            require(close(handle.getX(), current.getX()), "X coordinate mismatch");
            require(close(handle.getY(), current.getY()), "Y coordinate mismatch");
            require(close(handle.getZ(), current.getZ()), "Z coordinate mismatch");
        });

        check(sender, result, "Rotation and movement input", () -> {
            var originalYaw = handle.getYRot();
            var originalForward = handle.getZza();
            var originalStrafe = handle.getXxa();
            var testYaw = originalYaw > 120.0F ? originalYaw - 30.0F : originalYaw + 30.0F;
            try {
                handle.setYRot(testYaw);
                handle.setZza(0.5F);
                handle.setXxa(0.25F);
                require(close(handle.getYRot(), testYaw), "yaw did not update");
                require(close(handle.getZza(), 0.5F), "forward input did not update");
                require(close(handle.getXxa(), 0.25F), "strafe input did not update");
            } finally {
                handle.setYRot(originalYaw);
                handle.setZza(originalForward);
                handle.setXxa(originalStrafe);
            }
        });

        check(sender, result, "Jump action ticker", () -> {
            var ticker = bridge.createAction(fake, ActionType.JUMP, ActionSetting.once());
            try {
                ticker.tick();
            } finally {
                ticker.stop();
            }
        });

        var wasOp = fake.isOp();
        try {
            fake.setOp(true);
            check(sender, result, "Attack action", () -> testAttack(fake, fixture));
            check(sender, result, "Mining action", () -> testMining(fake, fixture));
            check(sender, result, "Item use action", () -> testUse(fake, fixture));
        } finally {
            fake.setOp(wasOp);
        }

        check(sender, result, "Fake player damage", () -> testDamage(fake));

        check(sender, result, "Temporary player removal", () ->
                require(manager.remove(name, "compatibility self-test complete"), "manager rejected removal"));

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            check(sender, result, "Residual state cleanup", () -> {
                require(manager.get(name) == null, "temporary player remains in manager");
                require(Bukkit.getPlayer(fake.getUniqueId()) == null, "temporary player remains online");
            });
            check(sender, result, "World fixture restoration", () -> {
                fixture.restore();
                require(fixture.isRestored(), "one or more temporary blocks were not restored");
            });
            finish(sender, result);
        }, 5L);
    }

    private void testAttack(Player fake, TestFixture fixture) {
        fixture.clearTargetBlock();
        var target = fixture.spawnAttackTarget();
        try {
            faceFixture(fake, fixture);
            var health = target.getHealth();
            var ticker = bridge.createAction(fake, ActionType.ATTACK, ActionSetting.once());
            try {
                ticker.tick();
            } finally {
                ticker.stop();
            }
            require(target.isDead() || target.getHealth() < health, "target did not take damage");
        } finally {
            target.remove();
        }
    }

    private void testDamage(Player fake) {
        var maxHealth = fake.getAttribute(Attribute.MAX_HEALTH);
        require(maxHealth != null, "fake player has no max health attribute");

        var wasInvulnerable = FakeplayerFeatureUtils.isInvulnerable(fake);
        var health = fake.getHealth();
        try {
            FakeplayerFeatureUtils.setInvulnerable(fake, false);
            fake.setHealth(maxHealth.getValue());
            fake.damage(1.0D);
            require(fake.getHealth() < maxHealth.getValue(), "fake player did not take damage");
        } finally {
            fake.setHealth(health);
            FakeplayerFeatureUtils.setInvulnerable(fake, wasInvulnerable);
        }
    }

    private void testMining(Player fake, TestFixture fixture) {
        fixture.setTargetBlock(Material.STONE);
        faceFixture(fake, fixture);
        var previousMode = fake.getGameMode();
        var previousItem = fake.getInventory().getItemInMainHand();
        var ticker = bridge.createAction(fake, ActionType.MINE, ActionSetting.once());
        try {
            fake.setGameMode(GameMode.CREATIVE);
            fake.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
            for (var i = 0; i < 10 && !fixture.targetBlock().isEmpty(); i++) {
                ticker.tick();
            }
            require(fixture.targetBlock().isEmpty(), "temporary stone block was not broken");
        } finally {
            ticker.stop();
            fake.getInventory().setItemInMainHand(previousItem);
            fake.setGameMode(previousMode);
        }
    }

    private void testUse(Player fake, TestFixture fixture) {
        fixture.setTargetBlock(Material.CHEST);
        faceFixture(fake, fixture);
        var previousItem = fake.getInventory().getItemInMainHand();
        var ticker = bridge.createAction(fake, ActionType.USE, ActionSetting.once());
        try {
            fake.getInventory().setItemInMainHand(null);
            ticker.tick();
            require(
                    fake.getOpenInventory().getTopInventory().getType() == InventoryType.CHEST,
                    "temporary chest was not opened"
            );
        } finally {
            ticker.stop();
            fake.closeInventory();
            fake.getInventory().setItemInMainHand(previousItem);
        }
    }

    private void faceFixture(Player fake, TestFixture fixture) {
        var location = fixture.spawnLocation();
        bridge.fromPlayer(fake).absMoveTo(
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    private void failSpawn(CommandSender sender, String name, TestFixture fixture, Throwable throwable) {
        var cause = unwrap(throwable);
        send(sender, RED, "FAIL: Temporary player spawn - " + describe(cause));
        log.log(Level.WARNING, "Fakeplayer self-test failed while spawning " + name, cause);
        manager.remove(name, "compatibility self-test failed");
        fixture.restore();
        running.set(false);
    }

    private void check(CommandSender sender, TestResult result, String label, CheckedRunnable check) {
        try {
            check.run();
            result.passed++;
            send(sender, GREEN, "PASS: " + label);
        } catch (Throwable throwable) {
            result.failed++;
            send(sender, RED, "FAIL: " + label + " - " + describe(throwable));
            log.log(Level.WARNING, "Fakeplayer self-test failed: " + label, throwable);
        }
    }

    private void finish(CommandSender sender, TestResult result) {
        var color = result.failed == 0 ? GREEN : RED;
        send(sender, color, "Completed: " + result.passed + " passed, " + result.failed + " failed.");
        send(sender, GRAY, "Inventory editing and restart persistence still require manual testing.");
        running.set(false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 0.001D;
    }

    private static Throwable unwrap(Throwable throwable) {
        var current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String describe(Throwable throwable) {
        var message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static void send(CommandSender sender, net.kyori.adventure.text.format.NamedTextColor color, String message) {
        sender.sendMessage(text("[FakePlayer Test] ", AQUA).append(text(message, color)));
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class TestResult {
        private int passed;
        private int failed;
    }

    private static final class TestFixture {

        private final Location spawnLocation;
        private final org.bukkit.block.Block platformBlock;
        private final org.bukkit.block.Block targetBlock;
        private final List<BlockState> originalStates;

        private TestFixture(
                Location spawnLocation,
                org.bukkit.block.Block platformBlock,
                org.bukkit.block.Block targetBlock,
                List<BlockState> originalStates
        ) {
            this.spawnLocation = spawnLocation;
            this.platformBlock = platformBlock;
            this.targetBlock = targetBlock;
            this.originalStates = originalStates;
        }

        private static TestFixture create(CommandSender sender) {
            var reference = sender instanceof Player player
                    ? player.getLocation()
                    : Bukkit.getWorlds().get(0).getSpawnLocation();
            var world = reference.getWorld();
            var x = reference.getBlockX();
            var z = reference.getBlockZ();
            var y = Math.max(world.getMinHeight() + 4, world.getHighestBlockYAt(x, z) + 4);
            y = Math.min(y, world.getMaxHeight() - 5);

            var platform = world.getBlockAt(x, y, z);
            var target = world.getBlockAt(x, y + 2, z + 3);
            var states = new ArrayList<BlockState>();
            states.add(platform.getState());
            states.add(target.getState());

            platform.setType(Material.BARRIER, false);
            target.setType(Material.AIR, false);
            var spawn = new Location(world, x + 0.5D, y + 1.0D, z + 0.5D, 0.0F, 0.0F);
            return new TestFixture(spawn, platform, target, states);
        }

        private Location spawnLocation() {
            return spawnLocation.clone();
        }

        private org.bukkit.block.Block targetBlock() {
            return targetBlock;
        }

        private void setTargetBlock(Material material) {
            targetBlock.setType(material, false);
        }

        private void clearTargetBlock() {
            setTargetBlock(Material.AIR);
        }

        private Zombie spawnAttackTarget() {
            return spawnLocation.getWorld().spawn(
                    spawnLocation.clone().add(0.0D, 0.0D, 2.5D),
                    Zombie.class,
                    zombie -> {
                        zombie.setAI(false);
                        zombie.setGravity(false);
                        zombie.setSilent(true);
                        zombie.setRemoveWhenFarAway(false);
                    }
            );
        }

        private void restore() {
            for (var i = originalStates.size() - 1; i >= 0; i--) {
                originalStates.get(i).update(true, false);
            }
        }

        private boolean isRestored() {
            return platformBlock.getType() == originalStates.get(0).getType()
                    && targetBlock.getType() == originalStates.get(1).getType();
        }
    }
}
