package com.dervarex.unweird.game;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Reads the fields of games, levels and things, and creates instances of their (inner) classes. */
final class Members {

    private Members() {
    }

    /** Non-static fields declared by the user's classes of {@code holder}, superclass first, in declaration order. */
    static List<Field> fields(Object holder) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = holder.getClass(); c != null && c != Object.class && !isLibrary(c); c = c.getSuperclass()) {
            chain.addFirst(c);
        }
        List<Field> result = new ArrayList<>();
        for (Class<?> c : chain) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    field.setAccessible(true);
                    result.add(field);
                }
            }
        }
        return result;
    }

    /** Field values of the given type: single fields, arrays and lists of them. */
    static <T> List<T> valuesOf(Object holder, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Field field : fields(holder)) {
            Object value = get(field, holder);
            collect(value, type, result);
        }
        return result;
    }

    private static <T> void collect(Object value, Class<T> type, List<T> into) {
        if (value == null) {
            return;
        }
        if (type.isInstance(value)) {
            into.add(type.cast(value));
        } else if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                collect(Array.get(value, i), type, into);
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collect(element, type, into);
            }
        }
    }

    /** Gives every unnamed {@link Sound} field of {@code holder} its field's name. */
    static void nameSounds(Object holder) {
        for (Field field : fields(holder)) {
            if (field.getType() == Sound.class && get(field, holder) instanceof Sound sound && sound.name == null) {
                sound.name = field.getName();
                sound.owner = holder.getClass();
            }
        }
    }

    static Object get(Field field, Object holder) {
        try {
            return field.get(holder);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("unweird-game: can't read " + field, e);
        }
    }

    /**
     * A new instance of {@code type}. Inner classes get one of {@code outers} as their enclosing instance (the game,
     * the current level, ...).
     */
    static <T> T create(Class<T> type, Object... outers) {
        try {
            if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
                Class<?> enclosing = type.getEnclosingClass();
                for (Object outer : outers) {
                    if (enclosing.isInstance(outer)) {
                        Constructor<T> constructor = type.getDeclaredConstructor(enclosing);
                        constructor.setAccessible(true);
                        return constructor.newInstance(outer);
                    }
                }
                throw new IllegalStateException("unweird-game: can't create a " + type.getSimpleName() + ": it's declared inside "
                        + enclosing.getSimpleName() + ", which isn't running. Declare it inside your game class");
            }
            if (type.isLocalClass() || type.isAnonymousClass()) {
                throw new IllegalStateException("unweird-game: can't create a " + type.getName()
                        + ": declare the class inside your game class, not inside a method");
            }
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("unweird-game: " + type.getSimpleName() + " needs a constructor without parameters", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("unweird-game: creating " + type.getSimpleName() + " failed", cause);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("unweird-game: can't create " + type.getSimpleName(), e);
        }
    }

    private static final java.security.CodeSource OWN = Members.class.getProtectionDomain().getCodeSource();

    /** One of unweird's classes (not the game's): its fields and methods aren't the user's. */
    static boolean isLibrary(Class<?> c) {
        String pkg = c.getPackageName();
        if (pkg.equals("com.dervarex.unweird") || pkg.startsWith("com.dervarex.unweird.") && !pkg.equals(Members.class.getPackageName())) {
            return true;
        }
        if (!pkg.equals(Members.class.getPackageName())) {
            return false;
        }
        java.security.CodeSource source = c.getProtectionDomain().getCodeSource();
        return OWN == null || source == null || OWN.getLocation().equals(source.getLocation());
    }
}
