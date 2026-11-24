package com.dervarex.unweird;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the field order that unweird-processor recorded at compile time ({@value #INDEX})
 * and turns a container's widget fields into its children.
 */
final class FieldIndex {

    static final String INDEX = "META-INF/unweird/fields.idx";

    private static volatile Map<String, List<String>> index;

    private FieldIndex() {
    }

    /** The widget fields of {@code owner}, superclass fields first, each class in declaration order. */
    static List<Widget> widgetsOf(Container owner) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> type = owner.getClass(); !isCoreBase(type); type = type.getSuperclass()) {
            chain.add(0, type);
        }

        List<Widget> widgets = new ArrayList<>();
        for (Class<?> type : chain) {
            List<String> names = index().get(type.getName());
            if (names == null) {
                throw new IllegalStateException(missingMessage(type));
            }
            for (String name : names) {
                Object value = read(owner, type, name);
                if (value instanceof Widget widget) {
                    widgets.add(widget);
                }
            }
        }
        return widgets;
    }

    private static boolean isCoreBase(Class<?> type) {
        return type == Container.class || type == Screen.class || type == Component.class || type == Object.class;
    }

    private static Object read(Container owner, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("unweird: field '" + name + "' of " + type.getName()
                    + " is in the field index but not in the class. Rebuild the project (the index is stale).", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("unweird: can't read field '" + name + "' of " + type.getName(), e);
        }
    }

    private static String missingMessage(Class<?> type) {
        if (type.isAnonymousClass() || type.isLocalClass()) {
            return "unweird: " + type.getName() + " is an anonymous or local class, so its fields can't be added automatically. "
                    + "Make it a named class, or call add(...) to list its widgets.";
        }
        return "unweird: no field order recorded for " + type.getName() + ".\n"
                + "Add the annotation processor to the module that contains it, e.g. in build.gradle.kts:\n"
                + "    annotationProcessor(\"com.dervarex:unweird-processor:<version>\")\n"
                + "or call add(...) in that class to list its widgets yourself.";
    }

    private static Map<String, List<String>> index() {
        Map<String, List<String>> loaded = index;
        if (loaded == null) {
            synchronized (FieldIndex.class) {
                loaded = index;
                if (loaded == null) {
                    loaded = load();
                    index = loaded;
                }
            }
        }
        return loaded;
    }

    private static Map<String, List<String>> load() {
        Map<String, List<String>> result = new HashMap<>();
        for (String line : IndexFiles.lines(INDEX)) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String fields = line.substring(eq + 1).strip();
            result.put(line.substring(0, eq).strip(),
                    fields.isEmpty() ? List.of() : List.copyOf(Arrays.asList(fields.split(","))));
        }
        return Map.copyOf(result);
    }
}
