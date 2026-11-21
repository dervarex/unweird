package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Shortcut;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Finds which shortcut a key press belongs to: focused component outwards, the screen, its docks, then app-wide. */
final class Shortcuts {

    /** {@code @Shortcut} methods per class, found once. */
    private static final Map<Class<?>, List<Annotated>> ANNOTATED = new ConcurrentHashMap<>();
    /** App-wide shortcuts from {@link Unweird#shortcut}. */
    static final Map<KeyPress.Shortcut, Runnable> GLOBAL = new ConcurrentHashMap<>();

    private record Annotated(KeyPress.Shortcut keys, Method method) {
    }

    private Shortcuts() {
    }

    /** Runs the matching shortcut, if any. Returns whether one ran. */
    static boolean dispatch(KeyPress key, Widget focused, Screen screen, Host host) {
        List<Container> chain = new ArrayList<>();
        for (Widget w = focused; w != null; w = w.parent) {
            if (w instanceof Container container && !chain.contains(container)) {
                chain.add(container);
            }
        }
        if (screen != null) {
            if (!chain.contains(screen)) {
                chain.add(screen);
            }
            for (DockSlot slot : screen.docks) {
                if (!chain.contains(slot.component)) {
                    chain.add(slot.component);
                }
            }
        }
        for (Container container : chain) {
            Runnable action = find(container, key);
            if (action != null) {
                host.safely(container, action);
                return true;
            }
        }
        for (Map.Entry<KeyPress.Shortcut, Runnable> entry : GLOBAL.entrySet()) {
            if (entry.getKey().matches(key)) {
                host.safely(null, entry.getValue());
                return true;
            }
        }
        return false;
    }

    private static Runnable find(Container container, KeyPress key) {
        for (Map.Entry<KeyPress.Shortcut, Runnable> entry : container.shortcuts().entrySet()) {
            if (entry.getKey().matches(key)) {
                return entry.getValue();
            }
        }
        for (Annotated annotated : annotated(container.getClass())) {
            if (annotated.keys.matches(key)) {
                return () -> invoke(annotated.method, container);
            }
        }
        return null;
    }

    static List<Annotated> annotated(Class<?> type) {
        return ANNOTATED.computeIfAbsent(type, t -> {
            List<Annotated> found = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    Shortcut shortcut = method.getAnnotation(Shortcut.class);
                    if (shortcut == null) {
                        continue;
                    }
                    if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) {
                        throw new IllegalStateException("@Shortcut needs an instance method without parameters: " + c.getName() + "." + method.getName());
                    }
                    method.setAccessible(true);
                    for (String pattern : shortcut.value()) {
                        found.add(new Annotated(KeyPress.Shortcut.parse(pattern), method));
                    }
                }
            }
            return List.copyOf(found);
        });
    }

    private static void invoke(Method method, Object target) {
        try {
            method.invoke(target);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<KeyPress.Shortcut, Runnable> newMap() {
        return new LinkedHashMap<>();
    }
}
