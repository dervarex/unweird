package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Fresh;

import java.util.HashMap;
import java.util.Map;

/** Screen instances: one cached instance per class, except {@link Fresh @Fresh} screens which are new every time. */
final class Screens {

    private final Map<Class<? extends Screen>, Screen> cache = new HashMap<>();

    Screen get(Class<? extends Screen> type) {
        if (type.isAnnotationPresent(Fresh.class)) {
            return Instances.create(type);
        }
        Screen screen = cache.get(type);
        if (screen == null) {
            screen = Instances.create(type);
            cache.put(type, screen);
        }
        return screen;
    }
}
