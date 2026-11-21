package com.dervarex.unweird;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One widget per item of a live list: {@code new Each<>(() -> results, ProjectRow::new)}.
 *
 * <p>The list is re-read after every event. Each item keeps its widget for as long as it stays in the list
 * (items are matched with {@code equals}, so records work well), so state inside a row survives
 * re-sorting and re-searching. New items get a new widget, removed items lose theirs.
 */
public class Each<T> extends Component {

    private final Supplier<? extends List<T>> items;
    private final Function<? super T, ? extends Widget> view;
    private Map<T, Widget> views = new LinkedHashMap<>();
    private List<T> current = List.of();

    public Each(Supplier<? extends List<T>> items, Function<? super T, ? extends Widget> view) {
        this.items = Objects.requireNonNull(items, "items");
        this.view = Objects.requireNonNull(view, "view");
        add(); // children come from the list, never from fields
    }

    /** The items currently shown. */
    public List<T> items() {
        return current;
    }

    /** The widget showing {@code item}, or null if it isn't in the list (right now). */
    public Widget viewOf(T item) {
        return views.get(item);
    }

    @Override
    protected void update() {
        List<T> next = items.get();
        next = next == null ? List.of() : List.copyOf(next); // also rejects null items
        if (!next.equals(current)) {
            Map<T, Widget> nextViews = new LinkedHashMap<>();
            List<Widget> widgets = new ArrayList<>();
            for (T item : next) {
                if (nextViews.containsKey(item)) {
                    throw new IllegalStateException("Each needs distinct items, but " + item + " is in the list twice");
                }
                Widget widget = views.get(item);
                if (widget == null) {
                    widget = Objects.requireNonNull(view.apply(item), "the view function returned null for " + item);
                }
                nextViews.put(item, widget);
                widgets.add(widget);
            }
            views = nextViews;
            current = next;
            replaceChildren(widgets);
        }
        super.update();
    }
}
