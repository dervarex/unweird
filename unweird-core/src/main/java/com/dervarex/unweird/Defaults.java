package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Default;
import com.dervarex.unweird.annotation.Without;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link Default @Default} components: found through the index unweird-processor writes,
 * created once, and shared by every screen that doesn't opt out with {@link Without @Without}.
 */
final class Defaults {

    static final String INDEX = "META-INF/unweird/defaults.idx";

    private static List<Class<? extends Component>> types;
    private static final Map<Class<?>, Component> instances = new HashMap<>();
    private static final Set<Class<?>> creating = new HashSet<>();

    private Defaults() {
    }

    /** All default component classes, sorted by class name (that's also their stacking order on a side). */
    static synchronized List<Class<? extends Component>> types() {
        if (types == null) {
            List<Class<? extends Component>> found = new ArrayList<>();
            for (String name : IndexFiles.lines(INDEX)) {
                Class<?> type;
                try {
                    type = Class.forName(name, false, IndexFiles.loader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("unweird: @Default class " + name + " is in the index but missing. Rebuild the project.", e);
                }
                if (!Component.class.isAssignableFrom(type) || !type.isAnnotationPresent(Default.class)) {
                    throw new IllegalStateException("unweird: " + name + " is in the @Default index but isn't a @Default Component. Rebuild the project.");
                }
                found.add(type.asSubclass(Component.class));
            }
            found.sort(Comparator.comparing(Class::getName));
            types = List.copyOf(found);
        }
        return types;
    }

    /** The one shared instance of a {@link Default @Default} component. */
    static synchronized <T extends Component> T shared(Class<T> type) {
        if (!types().contains(type)) {
            throw new IllegalArgumentException("unweird: " + type.getName() + " isn't marked @Default, so it has no shared instance"
                    + (type.isAnnotationPresent(Default.class) ? " (it is annotated, but missing from the index: is unweird-processor set up for its module?)" : ""));
        }
        Component instance = instances.get(type);
        if (instance == null) {
            if (!creating.add(type)) {
                throw new IllegalStateException("unweird: " + type.getName() + " asks for its own shared instance while being created");
            }
            try {
                instance = Instances.create(type);
            } finally {
                creating.remove(type);
            }
            instances.put(type, instance);
        }
        return type.cast(instance);
    }

    /** The docks a screen of this class shows: every default except those listed in its {@link Without @Without}. */
    static List<DockSlot> forScreen(Class<? extends Screen> screenType) {
        Set<Class<?>> excluded = new HashSet<>();
        Without without = screenType.getAnnotation(Without.class);
        if (without != null) {
            for (Class<?> type : without.value()) {
                if (!types().contains(type)) {
                    throw new IllegalStateException("unweird: " + screenType.getName() + " says @Without(" + type.getSimpleName()
                            + ".class), but " + type.getSimpleName() + " isn't a @Default component");
                }
                excluded.add(type);
            }
        }
        List<DockSlot> docks = new ArrayList<>();
        for (Class<? extends Component> type : types()) {
            if (!excluded.contains(type)) {
                docks.add(new DockSlot(shared(type), type.getAnnotation(Default.class).value()));
            }
        }
        return docks;
    }
}
