package io.github.hello09x.fakeplayer.core.util;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class VersionUtils {

    private VersionUtils() {
    }

    public static @NotNull String getMinecraftVersion() {
        return Bukkit.getMinecraftVersion();
    }

    public static @NotNull String getMinecraftMajorMinor() {
        var parts = getMinecraftVersion().split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return getMinecraftVersion();
    }
    public static boolean isAtLeast(int major, int minor, int patch) {
        var parts = getMinecraftVersion().split("\\.");
        var expected = new int[]{major, minor, patch};
        for (var i = 0; i < expected.length; i++) {
            var current = i < parts.length ? Integer.parseInt(parts[i]) : 0;
            if (current != expected[i]) {
                return current > expected[i];
            }
        }
        return true;
    }


    public static boolean isSupported(@NotNull Set<String> supports) {
        var full = getMinecraftVersion();
        if (supports.contains(full)) {
            return true;
        }
        return supports.contains(getMinecraftMajorMinor());
    }
}
