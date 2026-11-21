package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.Objects;

/**
 * Window and app settings, set before starting:
 *
 * <pre>{@code
 * Unweird.app("My Launcher")
 *         .size(1100, 720).minSize(800, 520)
 *         .icon("/images/logo.png")
 *         .remember()
 *         .start(LoginScreen.class);
 * }</pre>
 *
 * The window title is the screen's {@code @Title} followed by the app name ("Sign in — My Launcher").
 */
public final class App {

    final String name;
    int width = 960;
    int height = 640;
    int minWidth;
    int minHeight;
    String icon;
    boolean remember;
    boolean maximized;
    final java.util.List<String> fallbackFonts = new java.util.ArrayList<>();

    App(String name) {
        this.name = name;
    }

    /** Initial window size in logical pixels (also the size used when there's nothing remembered yet). */
    public App size(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        this.width = width;
        this.height = height;
        return this;
    }

    /** The window can't be made smaller than this. */
    public App minSize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("minimum size can't be negative");
        }
        this.minWidth = width;
        this.minHeight = height;
        return this;
    }

    /** Window/taskbar icon from the classpath, e.g. {@code "/images/logo.png"}. Ignored where the platform has no window icons (Wayland, macOS). */
    public App icon(String resource) {
        this.icon = Objects.requireNonNull(resource, "resource");
        return this;
    }

    /** Remembers position, size and maximized state between runs (stored in the user's config folder, under the app name). */
    public App remember() {
        this.remember = true;
        return this;
    }

    /** Starts maximized (unless {@link #remember()} knows better). */
    public App maximized() {
        this.maximized = true;
        return this;
    }

    /**
     * Another font (classpath resource, e.g. {@code "/fonts/NotoSansJP-Regular.ttf"}) to draw characters the built-in
     * fonts don't have, such as Chinese or Japanese. Emoji and common symbols are already covered.
     */
    public App fallbackFont(String resource) {
        fallbackFonts.add(Objects.requireNonNull(resource, "resource"));
        return this;
    }

    public App theme(Theme theme) {
        Unweird.theme(theme);
        return this;
    }

    /** Opens the window showing {@code firstScreen} and runs until it is closed. See {@link Unweird#start}. */
    public void start(Class<? extends Screen> firstScreen) {
        Unweird.start(this, firstScreen);
    }
}
