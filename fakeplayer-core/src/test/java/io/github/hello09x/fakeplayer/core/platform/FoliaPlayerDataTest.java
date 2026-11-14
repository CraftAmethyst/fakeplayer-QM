package io.github.hello09x.fakeplayer.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FoliaPlayerDataTest {
    @TempDir Path directory;

    public static void write(String contents, Path target) throws IOException { Files.writeString(target, contents); }
    public static void fail(String contents, Path target) throws IOException {
        Files.writeString(target, "partial compressed data");
        throw new IOException("disk full");
    }

    @Test void preservesNativeFilenameAndPreviousCompleteBackup() throws Exception {
        var target = directory.resolve("players/data/00000000-0000-0000-0000-000000000001.dat");
        var method = getClass().getMethod("write", String.class, Path.class);
        new FoliaPlayerData.Snapshot("first complete tag", target, method).write();
        new FoliaPlayerData.Snapshot("second complete tag", target, method).write();
        assertEquals("second complete tag", Files.readString(target));
        assertEquals("first complete tag", Files.readString(target.resolveSibling(target.getFileName() + "_old")));
        try (var files = Files.list(target.getParent())) { assertEquals(2, files.count()); }
    }

    @Test void failedCompressionLeavesLastGoodSaveAndCleansTemporaryFile() throws Exception {
        var target = directory.resolve("player.dat");
        Files.writeString(target, "last good save");
        var snapshot = new FoliaPlayerData.Snapshot("new tag", target, getClass().getMethod("fail", String.class, Path.class));
        var error = assertThrows(IOException.class, snapshot::write);
        assertEquals("disk full", error.getCause().getMessage());
        assertEquals("last good save", Files.readString(target));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
