package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.Objects;

/** Entry point: {@code Unweird.start(LoginScreen.class);} */
public final class Unweird {

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
        Objects.requireNonNull(firstScreen, "firstScreen");
        if (window != null) {
            throw new IllegalStateException("unweird is already running");
        }
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true"); // image decoding via ImageIO must not start an AWT GUI next to GLFW
        }
        Window created = new Window(theme);
        window = created;
        screens = new Screens();
        try {
            navigate(firstScreen);
            created.run();
        } finally {
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
        if (previous != null && previous != next) {
            window.safely(previous, previous::onHide);
        }
        next.docks = Defaults.forScreen(type);
        current = next;
        window.show(next);
        window.safely(next, next::onShow);
    }
}
