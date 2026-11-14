package io.github.hello09x.fakeplayer.core.platform;

import org.bukkit.Server;

import java.lang.reflect.Modifier;

/** Initializes vanilla's mutable path cache before player construction can run in parallel. */
public final class FoliaWorldPaths {
    private FoliaWorldPaths() {}

    public static void prepare(Server server) {
        try {
            var nativeServer = server.getClass().getMethod("getServer").invoke(server);
            var resourceType = Class.forName("net.minecraft.world.level.storage.LevelResource", true,
                    nativeServer.getClass().getClassLoader());
            var getPath = nativeServer.getClass().getMethod("getWorldPath", resourceType);
            // Stats and advancements are first requested by ServerPlayer's constructor.
            // Warm all standard keys: another lazy insertion would still race with a read.
            for (var field : resourceType.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == resourceType) {
                    getPath.invoke(nativeServer, field.get(null));
                }
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Cannot initialize Folia world resource paths", error);
        }
    }
}
