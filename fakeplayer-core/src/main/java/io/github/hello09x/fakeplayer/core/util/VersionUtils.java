package io.github.hello09x.fakeplayer.core.util;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class VersionUtils {

    private VersionUtils() {
    }

    public static @NotNull String getMinecraftVersion() {
        return Bukkit.getBukkitVersion().split("-")[0];
    }

    public static @NotNull String getMinecraftMajorMinor() {
        var parts = getMinecraftVersion().split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return getMinecraftVersion();
    }

    public static boolean isSupported(@NotNull Set<String> supports) {
        var full = getMinecraftVersion();
        if (supports.contains(full)) {
            return true;
        }
        return supports.contains(getMinecraftMajorMinor());
    }
}
