package com.dervarex.unweird.game;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Finds images, sounds and models by name, so game code never mentions a file: class {@code CoinBox} looks for
 * {@code coin_box.png}, {@code coinbox.png} or {@code CoinBox.png} next to the game class, at the classpath root and in
 * {@code images/}, {@code sprites/}, {@code textures/}, {@code sounds/}, {@code audio/}, {@code music/},
 * {@code models/}.
 */
final class Assets {

    private static final String[] FOLDERS = {"", "images/", "sprites/", "textures/", "sounds/", "audio/", "music/", "models/"};
    private static final Map<String, Optional<URL>> FOUND = new ConcurrentHashMap<>();

    private Assets() {
    }

    /** The spellings a name is looked up under: {@code coin_box}, {@code coinbox}, {@code CoinBox}. */
    static List<String> spellings(String name) {
        Set<String> result = new LinkedHashSet<>();
        result.add(snake(name));
        result.add(name.toLowerCase(Locale.ROOT));
        result.add(name);
        return new ArrayList<>(result);
    }

    /** {@code CoinBox} → {@code coin_box}, {@code Level2} → {@code level2}. */
    static String snake(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(name.charAt(i - 1))) {
                out.append('_');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    /** {@code CoinJump} → {@code Coin Jump}. */
    static String words(String name) {
        return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
    }

    /** The first resource named {@code name} (any spelling) with one of the extensions, near {@code owner}. */
    static URL find(Class<?> owner, String name, String... extensions) {
        String key = owner.getName() + "|" + name + "|" + String.join(",", extensions);
        return FOUND.computeIfAbsent(key, k -> Optional.ofNullable(search(owner, name, extensions))).orElse(null);
    }

    private static URL search(Class<?> owner, String name, String... extensions) {
        Class<?> top = owner;
        while (top.getEnclosingClass() != null) {
            top = top.getEnclosingClass();
        }
        String packagePath = top.getPackageName().replace('.', '/');
        ClassLoader loader = top.getClassLoader() != null ? top.getClassLoader() : ClassLoader.getSystemClassLoader();
        for (String spelling : spellings(name)) {
            for (String extension : extensions) {
                String file = spelling + "." + extension;
                if (!packagePath.isEmpty()) {
                    URL url = loader.getResource(packagePath + "/" + file);
                    if (url != null) {
                        return url;
                    }
                }
                for (String folder : FOLDERS) {
                    URL url = loader.getResource(folder + file);
                    if (url != null) {
                        return url;
                    }
                }
            }
        }
        return null;
    }

    static byte[] read(URL url) {
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("unweird-game: can't read " + url, e);
        }
    }
}
