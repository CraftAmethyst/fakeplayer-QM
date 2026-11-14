package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.event.player.PlayerRespawnEvent;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Runtime adapters are limited to the explicitly supported Folia version families. */
public final class FoliaCompatibility {
    private FoliaCompatibility() {}

    private static final Map<String, String> ADAPTERS = Map.ofEntries(
            Map.entry("1.21.4", "1_21_4"), Map.entry("1.21.5", "1_21_5"),
            Map.entry("1.21.6", "1_21_6"), Map.entry("1.21.8", "1_21_8"),
            Map.entry("1.21.11", "1_21_11"), Map.entry("26.1", "26_1"),
            Map.entry("26.1.1", "26_1"), Map.entry("26.1.2", "26_1"),
            Map.entry("26.2", "26_2"), Map.entry("26.3", "26_3"));

    public static String adapterClass(String version) {
        var family = ADAPTERS.get(version);
        if (family == null) throw new IllegalStateException("Unsupported Folia version: " + version);
        return "io.github.hello09x.fakeplayer.v" + family + ".spi.NMSBridgeImpl";
    }

    public static void verifyRuntime(String version, ClassLoader loader) {
        adapterClass(version);
        try {
            var connection = Class.forName("net.minecraft.network.Connection", false, loader);
            var playerList = Class.forName("net.minecraft.server.players.PlayerList", false, loader);
            var player = Class.forName("net.minecraft.server.level.ServerPlayer", false, loader);
            player.getMethod("respawn", Consumer.class, PlayerRespawnEvent.RespawnReason.class);
            playerList.getMethod("pushPendingJoin", String.class, UUID.class, connection);
            playerList.getMethod("removeConnection", String.class, UUID.class, connection);
            var regionServer = Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, loader);
            regionServer.getMethod("addConnection", connection);
            regionServer.getMethod("removeConnection", connection);
            var join = java.util.Arrays.stream(playerList.getMethods()).filter(method -> method.getName().equals("placeNewPlayer")).findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("placeNewPlayer"));
            int expected = switch (version) {
                case "1.21.4", "1.21.5" -> 6;
                case "1.21.6", "1.21.8" -> 7;
                default -> 3;
            };
            if (join.getParameterCount() != expected) throw new NoSuchMethodException("placeNewPlayer expected " + expected + " parameters");
            for (var name : new String[]{"connectionsStateLock", "connectionByName", "connectionById"}) playerList.getDeclaredField(name);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Folia lifecycle signatures do not match the candidate adapter for " + version, error);
        }
    }
}
