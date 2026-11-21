package com.dervarex.unweird;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A widget made of other widgets. Base of {@link Screen} and {@link Component}.
 *
 * <p>Widget fields are added automatically in the order they are declared (superclass fields first).
 * Mark a field {@link com.dervarex.unweird.annotation.Skip @Skip} to leave it out, or call
 * {@link #add(Widget...)} to list the children yourself, which turns automatic adding off.
 */
public abstract class Container extends Widget {

    private static final float GAP = 8;

    private List<Widget> manual;
    private List<Widget> children;

    /** Shows exactly these widgets, in this order, instead of the declared fields. Can be called repeatedly. */
    protected final void add(Widget... widgets) {
        if (manual == null) {
            manual = new ArrayList<>();
        }
        for (Widget widget : widgets) {
            manual.add(Objects.requireNonNull(widget, "add(...) got a null widget"));
        }
        children = null;
    }

    /** Redraws soon. Only needed after changing state from a thread other than the UI thread. */
    protected final void refresh() {
        Unweird.refresh();
    }

    final List<Widget> children() {
        if (children == null) {
            List<Widget> resolved = manual != null ? List.copyOf(manual) : FieldIndex.widgetsOf(this);
            Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Widget child : resolved) {
                if (child == this) {
                    throw new IllegalStateException(getClass().getName() + " can't contain itself");
                }
                if (!seen.add(child)) {
                    throw new IllegalStateException("A " + child.getClass().getSimpleName() + " can only be in one place, but it's in "
                            + getClass().getName() + " twice");
                }
                if (child.parent != null && child.parent != this) {
                    throw new IllegalStateException("A " + child.getClass().getSimpleName() + " can only be in one place, but it's in both "
                            + child.parent.getClass().getName() + " and " + getClass().getName());
                }
                child.parent = this;
            }
            children = Collections.unmodifiableList(resolved);
        }
        return children;
    }

    @Override
    protected void update() {
        for (Widget child : children()) {
            child.update();
        }
    }

    /** Default layout: children stacked top to bottom at full width. */
    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        float width = 0;
        float height = 0;
        List<Widget> kids = children();
        for (Widget child : kids) {
            Size size = child.measure(canvas, maxWidth);
            width = Math.max(width, size.width());
            height += size.height();
        }
        height += GAP * Math.max(0, kids.size() - 1);
        return new Size(width, height);
    }

    @Override
    protected void arrange(Canvas canvas) {
        float cy = getY();
        for (Widget child : children()) {
            float h = child.measure(canvas, getWidth()).height();
            child.place(canvas, getX(), cy, getWidth(), h);
            cy += h + GAP;
        }
    }

    @Override
    protected void draw(Canvas canvas) {
        for (Widget child : children()) {
            child.draw(canvas);
        }
    }

    @Override
    Widget hitTest(float px, float py) {
        if (!contains(px, py)) {
            return null;
        }
        List<Widget> kids = children();
        for (int i = kids.size() - 1; i >= 0; i--) {
            Widget hit = kids.get(i).hitTest(px, py);
            if (hit != null) {
                return hit;
            }
        }
        return interactive() ? this : null;
    }

    /** All focusable widgets inside this container, in Tab order (declaration order, depth first). */
    final List<Widget> focusables() {
        List<Widget> result = new ArrayList<>();
        collectFocusables(result);
        return result;
    }

    private void collectFocusables(List<Widget> into) {
        for (Widget child : children()) {
            if (child.focusable()) {
                into.add(child);
            }
            if (child instanceof Container container) {
                container.collectFocusables(into);
            }
        }
    }
}
