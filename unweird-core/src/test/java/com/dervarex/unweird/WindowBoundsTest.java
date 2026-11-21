package com.dervarex.unweird;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowBoundsTest {

    @TempDir
    Path dir;

    @Test
    void savesAndLoads() {
        Path file = dir.resolve("app/window.properties");
        new WindowBounds(10, 20, 1100, 700, true).save(file);
        assertEquals(new WindowBounds(10, 20, 1100, 700, true), WindowBounds.load(file));
    }

    @Test
    void brokenOrMissingFilesMeanDefaults() throws Exception {
        assertNull(WindowBounds.load(dir.resolve("missing.properties")));
        Path broken = dir.resolve("broken.properties");
        Files.writeString(broken, "x=not a number");
        assertNull(WindowBounds.load(broken));
    }

    @Test
    void theFolderIsNamedAfterTheApp() {
        Path file = WindowBounds.file("My Launcher 2!");
        assertTrue(file.toString().replace('\\', '/').endsWith("my-launcher-2/window.properties"), file.toString());
    }

    @Test
    void appSettingsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> Unweird.app("x").size(0, 10));
        assertThrows(IllegalArgumentException.class, () -> Unweird.app("x").minSize(-1, 10));
    }
}
