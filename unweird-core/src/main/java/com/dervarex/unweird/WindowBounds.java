package com.dervarex.unweird;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Window position and size between runs: {@code <config dir>/<app>/window.properties}. */
record WindowBounds(int x, int y, int width, int height, boolean maximized) {

    static Path file(String appName) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        Path base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = appData != null ? Path.of(appData) : Path.of(home, "AppData", "Roaming");
        } else if (os.contains("mac")) {
            base = Path.of(home, "Library", "Application Support");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".config");
        }
        String folder = appName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return base.resolve(folder.isEmpty() ? "unweird-app" : folder).resolve("window.properties");
    }

    static WindowBounds load(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
            return new WindowBounds(
                    Integer.parseInt(properties.getProperty("x")),
                    Integer.parseInt(properties.getProperty("y")),
                    Integer.parseInt(properties.getProperty("width")),
                    Integer.parseInt(properties.getProperty("height")),
                    Boolean.parseBoolean(properties.getProperty("maximized")));
        } catch (IOException | RuntimeException e) {
            return null; // unreadable: just use the defaults
        }
    }

    void save(Path file) {
        Properties properties = new Properties();
        properties.setProperty("x", Integer.toString(x));
        properties.setProperty("y", Integer.toString(y));
        properties.setProperty("width", Integer.toString(width));
        properties.setProperty("height", Integer.toString(height));
        properties.setProperty("maximized", Boolean.toString(maximized));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "unweird window position");
            }
        } catch (IOException e) {
            System.err.println("unweird: couldn't save the window position to " + file + ": " + e.getMessage());
        }
    }
}
