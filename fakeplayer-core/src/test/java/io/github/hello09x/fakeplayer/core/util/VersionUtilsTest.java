package io.github.hello09x.fakeplayer.core.util;

import io.github.hello09x.fakeplayer.core.platform.FoliaCompatibility;
import org.bukkit.Bukkit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class VersionUtilsTest {
    @ParameterizedTest
    @CsvSource({
            "26.2,26.2.build.7-beta,26_2",
            "26.3,26.3.build.6-alpha,26_3",
            "26.1.2,26.1.2.build.8-stable,26_1",
            "1.21.4,1.21.4-R0.1-SNAPSHOT,1_21_4",
            "1.21.11,1.21.11-R0.1-SNAPSHOT,1_21_11"
    })
    void adapterSelectionAndVersionComparisonUseGameVersion(String game, String api, String adapter) {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getMinecraftVersion).thenReturn(game);
            bukkit.when(Bukkit::getBukkitVersion).thenReturn(api);
            assertEquals(game, VersionUtils.getMinecraftVersion());
            assertTrue(VersionUtils.isSupported(Set.of(game)));
            assertEquals("io.github.hello09x.fakeplayer.v" + adapter + ".spi.NMSBridgeImpl",
                    FoliaCompatibility.adapterClass(VersionUtils.getMinecraftVersion()));
            var parts = game.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
            assertTrue(VersionUtils.isAtLeast(major, minor, patch));
            assertFalse(VersionUtils.isAtLeast(major, minor, patch + 1));
        }
    }
}
