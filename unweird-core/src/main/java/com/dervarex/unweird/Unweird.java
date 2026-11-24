package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.Objects;

/** Entry point: {@code Unweird.start(LoginScreen.class);} */
public final class Unweird {

    static final String DEFAULT_NAME = "unweird";

    private static volatile Window window;
    private static volatile Theme theme = Theme.light();
    private static Screens screens;
    private static Screen current;

    private Unweird() {
    }

    /**
     * Opens the window showing {@code firstScreen} and runs until it is closed.
     * Call this from the main thread; it takes over that thread as the UI thread.
     */
    public static void start(Class<? extends Screen> firstScreen) {
        start(new App(DEFAULT_NAME), firstScreen);
    }

    /** Window and app settings: {@code Unweird.app("My Launcher").size(1100, 720).remember().start(Home.class)}. */
    public static App app(String name) {
        return new App(Objects.requireNonNull(name, "name"));
    }

    static void start(App app, Class<? extends Screen> firstScreen) {
        Objects.requireNonNull(firstScreen, "firstScreen");
        if (window != null) {
            throw new IllegalStateException("unweird is already running");
        }
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true"); // image decoding via ImageIO must not start an AWT GUI next to GLFW
        }
        Window created = new Window(theme, app);
        window = created;
        created.shortcuts = (key, focused) -> Shortcuts.dispatch(key, focused, current, created);
        screens = new Screens();
        try {
            navigate(firstScreen);
            created.run();
        } finally {
            leave(current, List.of()); // the window is closing: run onHide and whileShown hooks for cleanup
            window = null;
            screens = null;
            current = null;
            created.close();
        }
    }

    /**
     * Shows another screen. Safe to call from any thread; from the UI thread it happens immediately.
     * Inside screens and components, just call {@code go(HomeScreen.class)}.
     */
    public static void go(Class<? extends Screen> screen) {
        Objects.requireNonNull(screen, "screen");
        Window current = window;
        if (current == null) {
            throw new IllegalStateException("unweird isn't running; go(...) works after Unweird.start(...)");
        }
        current.runOnUiThread(() -> navigate(screen));
    }

    /** The shared instance of a {@link com.dervarex.unweird.annotation.Default @Default} component. Safe from any thread. */
    public static <T extends Component> T shared(Class<T> type) {
        return Defaults.shared(Objects.requireNonNull(type, "type"));
    }

    /**
     * Runs {@code work} on a background (virtual) thread; {@code then}/{@code failed} handlers run on the UI thread.
     * Inside screens and components, just call {@code async(...)}.
     */
    public static <T> Async<T> async(java.util.concurrent.Callable<T> work) {
        return Async.start(work);
    }

    /** Runs {@code action} on the UI thread after the current event (or right away if unweird isn't running). Thread-safe. */
    public static void later(Runnable action) {
        Objects.requireNonNull(action, "action");
        Window current = window;
        if (current == null) {
            action.run();
        } else {
            current.runOnUiThread(() -> current.uiTasksLater(action));
        }
    }

    /** Runs {@code action} on the UI thread after {@code delay}. Thread-safe. Returns something to cancel it with. */
    public static Runnable after(java.time.Duration delay, Runnable action) {
        Objects.requireNonNull(action, "action");
        Window current = window;
        if (current == null) {
            throw new IllegalStateException("unweird isn't running; after(...) works after Unweird.start(...)");
        }
        return current.schedule(delay.toMillis() / 1000.0, action);
    }

    /** Opens {@code dialog} above everything. Thread-safe. Inside screens and components, just call {@code show(dialog)}. */
    public static void show(Dialog dialog) {
        Objects.requireNonNull(dialog, "dialog");
        Window current = window;
        if (current == null) {
            throw new IllegalStateException("unweird isn't running; dialogs can be shown after Unweird.start(...)");
        }
        current.runOnUiThread(() -> {
            if (dialog.layer != null) {
                return; // already open
            }
            ModalLayer layer = new ModalLayer(dialog);
            dialog.layer = layer;
            current.openOverlay(layer);
        });
    }

    static void closeOverlay(Overlay overlay) {
        Window current = window;
        if (current != null) {
            current.runOnUiThread(() -> current.closeOverlay(overlay));
        } else {
            overlay.closed();
        }
    }

    /** Closes the window and ends {@link #start}, as if the user closed it. Thread-safe. */
    public static void quit() {
        Window current = window;
        if (current != null) {
            current.requestClose();
        }
    }

    /** An app-wide shortcut, e.g. {@code Unweird.shortcut("F11", ...)}. Screens and components win over these. */
    public static void shortcut(String keys, Runnable action) {
        Shortcuts.GLOBAL.put(KeyPress.Shortcut.parse(keys), Objects.requireNonNull(action, "action"));
    }

    static void requestFrame() {
        Window current = window;
        if (current != null) {
            current.requestFrame();
        }
    }

    static void lockPointer(Widget widget, boolean lock) {
        Window current = window;
        if (current != null) {
            if (lock) {
                current.lockPointer(widget);
            } else {
                current.unlockPointer(widget);
            }
        }
    }

    /** Keys and mouse buttons held right now; empty sets without a window. */
    static java.util.Set<Integer> heldKeys() {
        Window current = window;
        return current == null ? java.util.Set.of() : current.heldKeys;
    }

    static java.util.Set<Integer> heldButtons() {
        Window current = window;
        return current == null ? java.util.Set.of() : current.heldButtons;
    }

    static void focus(Widget widget) {
        Window current = window;
        if (current != null) {
            current.runOnUiThread(() -> current.requestFocus(widget));
        }
    }

    /** Shows a short message in the corner. Thread-safe. */
    public static void toast(String message) {
        toast(Toast.info(message));
    }

    /** Shows a toast, e.g. {@code Unweird.toast(Toast.error("Couldn't connect"))}. Thread-safe. */
    public static void toast(Toast toast) {
        Objects.requireNonNull(toast, "toast");
        Window current = window;
        if (current == null) {
            System.out.println("unweird: [" + toast.kind() + "] " + toast.message());
            return;
        }
        current.runOnUiThread(() -> current.toast(toast));
    }

    /** The current theme. */
    public static Theme theme() {
        return theme;
    }

    /** Switches the theme, e.g. {@code Unweird.theme(Theme.dark())}. Call before {@link #start} or from the UI thread. */
    public static void theme(Theme next) {
        theme = Objects.requireNonNull(next, "theme");
        Window current = window;
        if (current != null) {
            current.theme(next);
        }
    }

    /**
     * Re-evaluates all lambdas and redraws soon. Safe to call from any thread.
     * Not needed inside handlers; the UI refreshes after every event anyway.
     */
    public static void refresh() {
        Window current = window;
        if (current != null) {
            current.requestRender();
        }
    }

    /** UI thread only. */
    private static void navigate(Class<? extends Screen> type) {
        Screen next = screens.get(type);
        Screen previous = current;
        List<DockSlot> nextDocks = Defaults.forScreen(type);
        if (previous != next) {
            leave(previous, nextDocks);
        }
        next.docks = nextDocks;
        current = next;
        window.show(next);
        next.attach();
        for (DockSlot slot : nextDocks) {
            slot.component.attach(); // no-op for docks that were already showing
        }
        window.safely(next, next::onShow);
    }

    /** {@code screen} goes away: onHide, then its whileShown hooks, and those of docks that won't stay. */
    private static void leave(Screen screen, List<DockSlot> stayingDocks) {
        if (screen == null) {
            return;
        }
        runSafely(screen, screen::onHide);
        screen.detach();
        for (DockSlot slot : screen.docks) {
            if (stayingDocks.stream().noneMatch(staying -> staying.component == slot.component)) {
                slot.component.detach();
            }
        }
    }

    /** Runs user code; an exception is reported instead of taking the app down. */
    static void runSafely(Widget source, Runnable code) {
        Window current = window;
        if (current != null) {
            current.safely(source, code);
            return;
        }
        try {
            code.run();
        } catch (RuntimeException e) {
            System.err.println("unweird: a handler on " + (source == null ? "?" : source.getClass().getSimpleName()) + " threw:");
            e.printStackTrace();
        }
    }

    // ---- opening links and files ----

    /**
     * Opens a link or file in the user's default app: {@code Unweird.open("https://example.com")}, also
     * {@code mailto:} links and file paths. Works without AWT's {@code Desktop} (unweird runs AWT headless).
     * Safe from any thread.
     *
     * @return false if no opener could be started (e.g. {@code xdg-open} is missing); show the link yourself then
     */
    public static boolean open(String target) {
        try {
            new ProcessBuilder(openCommand(System.getProperty("os.name", ""), target))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (java.io.IOException e) {
            System.err.println("unweird: couldn't open " + target + ": " + e.getMessage());
            return false;
        }
    }

    /** Opens a file (or folder) in the user's default app. See {@link #open(String)}. */
    public static boolean open(java.nio.file.Path file) {
        return open(file.toAbsolutePath().toString());
    }

    /** The command that opens {@code target} on this OS. */
    static List<String> openCommand(String osName, String target) {
        Objects.requireNonNull(target, "target");
        String trimmed = target.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nothing to open");
        }
        if (trimmed.startsWith("-")) {
            throw new IllegalArgumentException("Refusing to open \"" + trimmed + "\": it would be read as an option");
        }
        String os = osName.toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return List.of("rundll32", "url.dll,FileProtocolHandler", trimmed);
        }
        if (os.contains("mac")) {
            return List.of("open", trimmed);
        }
        return List.of("xdg-open", trimmed);
    }
}
