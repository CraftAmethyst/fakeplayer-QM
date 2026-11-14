package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Native player codecs are resolved separately from the Paper compile classpath. */
public final class FoliaPlayerData {
    private FoliaPlayerData() {}

    /** Modern Paper also separates disk loading from placeNewPlayer. */
    public static boolean requiresManualLoad() {
        return io.github.hello09x.fakeplayer.core.Main.getScheduler().isFolia()
                || io.github.hello09x.fakeplayer.core.util.VersionUtils.isAtLeast(1, 21, 11);
    }

    private static final class RuntimeCodec {
        private static final Codec INSTANCE = new Codec();
    }

    /** IO and data fixing only. Call on the persistence writer, before constructing the player. */
    public static Object read(UUID id, String name) {
        return RuntimeCodec.INSTANCE.read(id, name);
    }

    /** Apply only before region registration, on the initial spawn region. */
    public static void apply(Player player, Object tag, Location initialLocation) {
        if (tag != null) RuntimeCodec.INSTANCE.apply(player, tag, initialLocation);
    }

    /** Restore after the spawn dimension detour, which would otherwise dismount the saved vehicle. */
    public static void restoreAttachments(Player player, Object tag) {
        if (tag != null) RuntimeCodec.INSTANCE.restoreAttachments(player, tag);
    }

    /** Older placement restores game modes too; defer its attachment side effects until after teleports. */
    public static Optional<?> placementData(Player player, Object tag) {
        if (tag == null) return Optional.empty();
        return Optional.of(RuntimeCodec.INSTANCE.placementData(player, tag));
    }

    /** Capture on the owner. The returned value contains no live player or world references. */
    public static Snapshot capture(Player player) {
        return RuntimeCodec.INSTANCE.capture(player);
    }

    public static final class Snapshot {
        private final Object tag;
        private final Path target;
        private final Method writeCompressed;

        Snapshot(Object tag, Path target, Method writeCompressed) {
            this.tag = tag;
            this.target = target;
            this.writeCompressed = writeCompressed;
        }

        /** Compression and disk access are deliberately deferred to the serial writer. */
        public void write() throws IOException {
            Files.createDirectories(target.getParent());
            var temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                try { writeCompressed.invoke(null, tag, temporary); }
                catch (InvocationTargetException error) { throw new IOException("Native player data compression failed", error.getCause()); }
                catch (ReflectiveOperationException error) { throw new IOException("Cannot invoke native compression codec", error); }
                if (Files.exists(target)) Files.copy(target, target.resolveSibling(target.getFileName() + "_old"), StandardCopyOption.REPLACE_EXISTING);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    private static final class Codec {
        private final Object storage;
        private final Path directory;
        private final Class<?> compound;
        private final Method getHandle, save, load, writeCompressed, dataVersion, loadPearls, loadVehicle;
        private final Method outputFactory, outputResult, inputFactory, registryAccess;
        private final Method nativeRead;
        private final java.lang.reflect.Constructor<?> identity;
        private final java.lang.reflect.Constructor<?> collector;
        private final Method problems;
        private final Object reporter;

        private Codec() {
            try {
                var craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                getHandle = craftPlayer.getMethod("getHandle");
                var playerType = Class.forName("net.minecraft.server.level.ServerPlayer");
                var playerList = Bukkit.getServer().getClass().getMethod("getHandle").invoke(Bukkit.getServer());
                storage = Class.forName("net.minecraft.server.players.PlayerList").getField("playerIo").get(playerList);
                directory = ((File) storage.getClass().getMethod("getPlayerDir").invoke(storage)).toPath();
                compound = Class.forName("net.minecraft.nbt.CompoundTag");
                writeCompressed = Class.forName("net.minecraft.nbt.NbtIo").getMethod("writeCompressed", compound, Path.class);
                dataVersion = Class.forName("net.minecraft.nbt.NbtUtils").getMethod("addCurrentDataVersion", compound);
                save = Arrays.stream(playerType.getMethods()).filter(m -> m.getName().equals("saveWithoutId") && m.getParameterCount() == 1).findFirst().orElseThrow();
                load = playerType.getMethod("load", save.getParameterTypes()[0] == compound ? compound : Class.forName("net.minecraft.world.level.storage.ValueInput"));
                loadVehicle = Arrays.stream(playerType.getMethods()).filter(m -> m.getName().equals("loadAndSpawnParentVehicle") && m.getParameterCount() == 1)
                        .findFirst().orElseThrow();
                loadPearls = Arrays.stream(playerType.getMethods()).filter(m -> m.getName().equalsIgnoreCase("loadAndSpawnEnderPearls") && m.getParameterCount() == 1)
                        .findFirst().orElseThrow();
                if (save.getParameterTypes()[0] == compound) {
                    reporter = null; collector = null; problems = null; outputFactory = null; outputResult = null; inputFactory = null; registryAccess = null;
                } else {
                    var problem = Class.forName("net.minecraft.util.ProblemReporter");
                    reporter = problem.getField("DISCARDING").get(null);
                    var collectorType = Class.forName("net.minecraft.util.ProblemReporter$Collector");
                    collector = collectorType.getConstructor();
                    problems = collectorType.getMethod("getReport");
                    var provider = Class.forName("net.minecraft.core.HolderLookup$Provider");
                    var output = Class.forName("net.minecraft.world.level.storage.TagValueOutput");
                    outputFactory = output.getMethod("createWithContext", problem, provider);
                    outputResult = output.getMethod("buildResult");
                    inputFactory = Class.forName("net.minecraft.world.level.storage.TagValueInput").getMethod("create", problem, provider, compound);
                    registryAccess = playerType.getMethod("registryAccess");
                }
                var read = Arrays.stream(storage.getClass().getMethods()).filter(m -> m.getName().equals("load")
                        && m.getReturnType() == Optional.class
                        && (m.getParameterCount() == 1 && m.getParameterTypes()[0].getSimpleName().equals("NameAndId")
                        || m.getParameterCount() >= 2 && m.getParameterTypes()[0] == String.class && m.getParameterTypes()[1] == String.class))
                        .findFirst().orElseThrow();
                nativeRead = read;
                identity = read.getParameterCount() == 1 ? read.getParameterTypes()[0].getConstructor(UUID.class, String.class) : null;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported Folia player data codec", error);
            }
        }

        private Object read(UUID id, String name) {
            try {
                Object result = identity != null ? invoke(nativeRead, storage, identity.newInstance(id, name))
                        : nativeRead.getParameterCount() == 2 ? invoke(nativeRead, storage, name, id.toString())
                        : invoke(nativeRead, storage, name, id.toString(), reporter);
                return ((Optional<?>) result).orElse(null);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read native player data", error); }
        }

        private void apply(Player player, Object tag, Location initial) {
            // The saved inventory may belong to another region/world. Never temporarily
            // move the unregistered entity there while Entity.load reads its position.
            try {
                var put = compound.getMethod("put", String.class, Class.forName("net.minecraft.nbt.Tag"));
                invoke(put, tag, "Pos", doubles(initial.getX(), initial.getY(), initial.getZ()));
                compound.getMethod("putLong", String.class, long.class).invoke(tag, "WorldUUIDMost", initial.getWorld().getUID().getMostSignificantBits());
                compound.getMethod("putLong", String.class, long.class).invoke(tag, "WorldUUIDLeast", initial.getWorld().getUID().getLeastSignificantBits());
                var handle = invoke(getHandle, player);
                invoke(load, handle, inputFactory == null ? tag : invoke(inputFactory, null, reporter, invoke(registryAccess, handle), tag));
                io.github.hello09x.fakeplayer.core.Main.getInjector().getInstance(io.github.hello09x.fakeplayer.api.spi.NMSBridge.class)
                        .fromPlayer(player).absMoveTo(initial.getX(), initial.getY(), initial.getZ(), initial.getYaw(), initial.getPitch());
                player.setPersistent(false);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot restore native player data", error); }
        }

        private Object doubles(double... values) throws ReflectiveOperationException {
            var list = Class.forName("net.minecraft.nbt.ListTag").getConstructor().newInstance();
            var value = Class.forName("net.minecraft.nbt.DoubleTag").getMethod("valueOf", double.class);
            @SuppressWarnings("unchecked") var elements = (java.util.List<Object>) list;
            for (var number : values) elements.add(invoke(value, null, number));
            return list;
        }

        private void restoreAttachments(Player player, Object tag) {
            var handle = invoke(getHandle, player);
            var input = inputFactory == null ? tag
                    : invoke(inputFactory, null, reporter, invoke(registryAccess, handle), tag);
            invoke(loadVehicle, handle, loadVehicle.getParameterTypes()[0] == Optional.class ? Optional.of(tag) : input);
            invoke(loadPearls, handle, loadPearls.getParameterTypes()[0] == Optional.class ? Optional.of(tag) : input);
        }

        private Object placementData(Player player, Object tag) {
            try {
                var copy = invoke(compound.getMethod("copy"), tag);
                var remove = compound.getMethod("remove", String.class);
                invoke(remove, copy, "RootVehicle");
                invoke(remove, copy, "ender_pearls");
                if (inputFactory == null) return copy;
                var handle = invoke(getHandle, player);
                return invoke(inputFactory, null, reporter, invoke(registryAccess, handle), copy);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot prepare regional join data", error); }
        }

        private Snapshot capture(Player player) {
            try {
                var handle = invoke(getHandle, player);
                var report = collector == null ? null : collector.newInstance();
                var output = outputFactory == null ? compound.getConstructor().newInstance()
                        : invoke(outputFactory, null, report, invoke(registryAccess, handle));
                invoke(save, handle, output);
                if (report != null) {
                    var errors = (String) invoke(problems, report);
                    if (!errors.isEmpty()) throw new IllegalStateException("Incomplete native player snapshot: " + errors);
                }
                var tag = outputFactory == null ? output : invoke(outputResult, output);
                invoke(dataVersion, null, tag);
                return new Snapshot(tag, directory.resolve(player.getUniqueId() + ".dat"), writeCompressed);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot snapshot native player data", error); }
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException error) { throw new IllegalStateException("Native player data operation failed: " + method.getName(), error.getCause()); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot invoke native player data codec", error); }
    }
}
