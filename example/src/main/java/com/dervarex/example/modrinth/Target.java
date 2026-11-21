package com.dervarex.example.modrinth;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Where content gets installed and what it must be compatible with.
 *
 * @param gameDirectory the Minecraft game directory (the one containing {@code mods/}, {@code resourcepacks/}, ...)
 * @param gameVersion   Minecraft version, e.g. {@code "1.21.1"}; versions are filtered to it. Null = any.
 * @param loader        mod loader for mods, e.g. {@code "fabric"}, {@code "neoforge"}; null for vanilla
 */
public record Target(Path gameDirectory, String gameVersion, String loader) {

    public Target {
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        gameDirectory = gameDirectory.toAbsolutePath().normalize();
        loader = loader == null || loader.isBlank() ? null : loader.toLowerCase(Locale.ROOT);
    }

    /** A vanilla target (no mod loader). */
    public static Target of(Path gameDirectory, String gameVersion) {
        return new Target(gameDirectory, gameVersion, null);
    }

    public Target withLoader(String loader) {
        return new Target(gameDirectory, gameVersion, loader);
    }

    /** "Minecraft 1.21.1 with fabric", for messages. */
    String describe() {
        String game = gameVersion == null ? "this Minecraft version" : "Minecraft " + gameVersion;
        return loader == null ? game : game + " with " + loader;
    }
}
