package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Objects;

/** Entry point: {@code Unweird.start(LoginScreen.class);} */
public final class Unweird {

    private static volatile Window window;
    private static volatile Theme theme = Theme.light();

    private Unweird() {
    }

    /**
     * Opens the window showing {@code firstScreen} and runs until it is closed.
     * Call this from the main thread; it takes over that thread as the UI thread.
     */
    public static void start(Class<? extends Screen> firstScreen) {
        if (window != null) {
            throw new IllegalStateException("unweird is already running");
        }
        Screen screen = create(firstScreen);
        Window created = new Window(theme);
        window = created;
        try {
            created.show(screen);
            created.run();
        } finally {
            window = null;
            created.close();
        }
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
     * Not needed inside click handlers; the UI refreshes after every event anyway.
     */
    public static void refresh() {
        Window current = window;
        if (current != null) {
            current.requestRender();
        }
    }

    private static <S extends Screen> S create(Class<S> type) {
        Objects.requireNonNull(type, "screen");
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("unweird: " + type.getName() + " is abstract and can't be shown");
        }
        if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
            throw new IllegalArgumentException("unweird: " + type.getName() + " must be a top-level or static nested class");
        }
        try {
            Constructor<S> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("unweird: " + type.getName() + " needs a constructor without parameters", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("unweird: creating " + type.getName() + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("unweird: could not create " + type.getName(), e);
        }
    }
}
