package com.dervarex.unweird;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/** Creates screens and default components through their no-argument constructor, with readable errors. */
final class Instances {

    private Instances() {
    }

    static <T> T create(Class<T> type) {
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("unweird: " + type.getName() + " is abstract and can't be created");
        }
        if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
            throw new IllegalArgumentException("unweird: " + type.getName() + " must be a top-level or static nested class");
        }
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
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
