package com.dervarex.unweird.game;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The methods a game class may add by name ({@code tick()}, {@code touch(Coin c)}, {@code picked()}, …). Found by
 * reflection so they can have any visibility and need no {@code @Override}. An exception in one is printed and the
 * game goes on.
 */
final class Hooks {

    private static final Map<Class<?>, Hooks> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    /** No-argument hooks by name. */
    private final Map<String, Method> plain = new HashMap<>();
    /** One-argument hooks by name, most specific parameter type first. */
    private final Map<String, List<Method>> typed = new HashMap<>();

    private Hooks(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (Members.isLibrary(c)) {
                continue; // the library's own classes declare no hooks
            }
            for (Method method : c.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                if (method.getParameterCount() == 0) {
                    plain.putIfAbsent(method.getName(), accessible(method));
                } else if (method.getParameterCount() == 1) {
                    List<Method> list = typed.computeIfAbsent(method.getName(), k -> new ArrayList<>());
                    if (list.stream().noneMatch(m -> m.getParameterTypes()[0] == method.getParameterTypes()[0])) {
                        list.add(accessible(method));
                    }
                }
            }
        }
        for (List<Method> list : typed.values()) {
            list.sort((a, b) -> depth(b.getParameterTypes()[0]) - depth(a.getParameterTypes()[0]));
        }
    }

    static Hooks of(Class<?> type) {
        return CACHE.computeIfAbsent(type, Hooks::new);
    }

    boolean has(String name) {
        return plain.containsKey(name);
    }

    /** Calls {@code name()} on {@code target} if it declares it; returns whether it did. */
    boolean call(Object target, String name) {
        Method method = plain.get(name);
        if (method == null) {
            return false;
        }
        invoke(method, target);
        return true;
    }

    /** Calls the most specific {@code name(T)} that accepts {@code argument}; returns whether there was one. */
    boolean call(Object target, String name, Object argument) {
        List<Method> candidates = typed.get(name);
        if (candidates == null) {
            return false;
        }
        for (Method method : candidates) {
            if (method.getParameterTypes()[0].isInstance(argument)) {
                invoke(method, target, argument);
                return true;
            }
        }
        return false;
    }

    private static void invoke(Method method, Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error && !(cause instanceof StackOverflowError)) {
                throw error;
            }
            String where = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            if (reported.add(where + cause)) {
                System.err.println("unweird-game: " + where + "(...) failed, the game goes on:");
                cause.printStackTrace();
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("unweird-game: can't call " + method, e);
        }
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    private static int depth(Class<?> type) {
        int depth = 0;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            depth++;
        }
        return type.isInterface() ? 0 : depth;
    }
}
