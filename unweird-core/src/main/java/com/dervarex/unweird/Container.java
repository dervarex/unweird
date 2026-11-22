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

    private List<Widget> manual;
    private List<Widget> children;
    /** Children as last resolved, to tell which ones came and went. */
    private List<Widget> previousChildren = List.of();

    /** {@link #whileShown} hooks as {shown, hidden} pairs. */
    private final List<Runnable[]> lifecycle = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** On screen right now: part of the shown screen, or a dock that's showing. */
    private boolean attached;
    /** Set while this container is docked on a screen edge; switches to {@link Docking}'s layout. */
    Docking.Axis dockAxis;
    /** How the surrounding content is laid out; set by {@link Flow} before measuring. */
    Flow.Mode flowMode = Flow.Mode.PAGE;
    /** What the first text in this container becomes, if not set explicitly. */
    Text.Role headingRole = Text.Role.TITLE;
    /** Drawn on its own surface, see {@link com.dervarex.unweird.annotation.Card @Card}. */
    final boolean card = getClass().isAnnotationPresent(com.dervarex.unweird.annotation.Card.class);
    static final float CARD_PADDING = 16;

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

    /** Children that always come after the declared ones (a dialog's buttons). Re-read whenever children are resolved. */
    List<Widget> trailingChildren() {
        return List.of();
    }

    /** Replaces the children (used by {@link Each}). Widgets that are no longer children are released. */
    final void replaceChildren(List<Widget> next) {
        if (children != null) {
            Set<Widget> keep = Collections.newSetFromMap(new IdentityHashMap<>());
            keep.addAll(next);
            for (Widget old : children) {
                if (!keep.contains(old) && old.parent == this) {
                    old.parent = null;
                }
            }
        }
        manual = new ArrayList<>(next);
        children = null;
    }

    private final java.util.Map<KeyPress.Shortcut, Runnable> shortcuts = java.util.Collections.synchronizedMap(Shortcuts.newMap());

    /**
     * Runs {@code action} when {@code keys} (e.g. {@code "Ctrl+S"}) are pressed while this is on screen and the focused
     * widget doesn't use the keys itself. Registering the same keys again replaces the action.
     * For fixed shortcuts, {@link com.dervarex.unweird.annotation.Shortcut @Shortcut} on a method is shorter.
     */
    protected final void shortcut(String keys, Runnable action) {
        shortcuts.put(KeyPress.Shortcut.parse(keys), java.util.Objects.requireNonNull(action, "action"));
    }

    java.util.Map<KeyPress.Shortcut, Runnable> shortcuts() {
        synchronized (shortcuts) {
            return java.util.Map.copyOf(shortcuts);
        }
    }

    /** Opens a dialog above everything; see {@link Dialog}. */
    protected final void show(Dialog dialog) {
        Unweird.show(dialog);
    }

    /** Background work with the result on the UI thread: {@code async(() -> load()).then(r -> ...)}. See {@link Async}. */
    protected final <T> Async<T> async(java.util.concurrent.Callable<T> work) {
        return Unweird.async(work);
    }

    /** Shows a short message in the corner. */
    protected final void toast(String message) {
        Unweird.toast(message);
    }

    /** Shows a toast, e.g. {@code toast(Toast.error("Couldn't save"))}. */
    protected final void toast(Toast toast) {
        Unweird.toast(toast);
    }

    /** Redraws soon. Only needed after changing state from a thread other than the UI thread. */
    protected final void refresh() {
        Unweird.refresh();
    }

    /**
     * Shows another screen: {@code go(HomeScreen.class)}. Screens are created once and reused,
     * unless marked {@link com.dervarex.unweird.annotation.Fresh @Fresh}.
     */
    protected final void go(Class<? extends Screen> screen) {
        Unweird.go(screen);
    }

    /** The shared instance of a {@link com.dervarex.unweird.annotation.Default @Default} component, e.g. {@code shared(Sidebar.class)}. */
    protected final <T extends Component> T shared(Class<T> type) {
        return Unweird.shared(type);
    }

    final List<Widget> children() {
        if (children == null) {
            List<Widget> resolved = new ArrayList<>(manual != null ? manual : FieldIndex.widgetsOf(this));
            resolved.addAll(trailingChildren());
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
            if (attached) {
                attachChanges(previousChildren, children);
            }
            previousChildren = children;
        }
        return children;
    }

    // ---- on screen or not ----

    /**
     * Runs {@code onShown} whenever this screen or component appears on screen and {@code onHidden} when it goes away
     * (another screen is shown, it's removed from an {@link Each}, the window closes). For listeners on things outside
     * the UI:
     *
     * <pre>{@code
     * private final LoginStateChangeListener redraw = state -> refresh();
     * { whileShown(() -> AuthManager.addStateChangeListener(redraw), () -> AuthManager.removeStateChangeListener(redraw)); }
     * }</pre>
     *
     * If it's already on screen, {@code onShown} runs right away. Runs on the UI thread.
     */
    protected final void whileShown(Runnable onShown, Runnable onHidden) {
        Runnable[] hooks = {java.util.Objects.requireNonNull(onShown, "onShown"), java.util.Objects.requireNonNull(onHidden, "onHidden")};
        lifecycle.add(hooks);
        if (attached) {
            Unweird.runSafely(this, onShown);
        }
    }

    /** It's on screen now: run shown hooks, here first, then for every container inside. */
    final void attach() {
        if (attached) {
            return;
        }
        attached = true;
        for (Runnable[] hooks : lifecycle) {
            Unweird.runSafely(this, hooks[0]);
        }
        for (Widget child : children()) {
            if (child instanceof Container container) {
                container.attach();
            }
        }
    }

    /** It's gone from the screen: hidden hooks, inner containers first, in reverse order. */
    final void detach() {
        if (!attached) {
            return;
        }
        for (Widget child : children()) {
            if (child instanceof Container container) {
                container.detach();
            }
        }
        attached = false;
        List<Runnable[]> hooks = new ArrayList<>(lifecycle);
        java.util.Collections.reverse(hooks);
        for (Runnable[] pair : hooks) {
            Unweird.runSafely(this, pair[1]);
        }
    }

    final boolean isAttached() {
        return attached;
    }

    private static void attachChanges(List<Widget> before, List<Widget> after) {
        Set<Widget> now = Collections.newSetFromMap(new IdentityHashMap<>());
        now.addAll(after);
        Set<Widget> was = Collections.newSetFromMap(new IdentityHashMap<>());
        was.addAll(before);
        for (Widget old : before) {
            if (!now.contains(old) && old instanceof Container container) {
                container.detach();
            }
        }
        for (Widget fresh : after) {
            if (!was.contains(fresh) && fresh instanceof Container container) {
                container.attach();
            }
        }
    }

    @Override
    protected void update() {
        for (Widget child : children()) {
            child.evaluateConditions();
            child.update();
        }
    }

    /** Inside content, a container takes the full width and lays its children out like a screen would (see {@link Flow}). */
    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        if (dockAxis != null) {
            return Docking.measure(this, canvas, maxWidth);
        }
        float inset = card ? CARD_PADDING * 2 : 0;
        return new Size(maxWidth, Flow.column(this, canvas, Math.max(0, maxWidth - inset), false, 0, 0) + inset);
    }

    @Override
    protected void arrange(Canvas canvas) {
        if (dockAxis != null) {
            Docking.arrange(this, canvas);
            return;
        }
        float pad = card ? CARD_PADDING : 0;
        Flow.column(this, canvas, Math.max(0, getWidth() - pad * 2), true, getX() + pad, getY() + pad);
    }

    @Override
    protected void draw(Canvas canvas) {
        if (card && dockAxis == null) {
            var theme = canvas.getTheme();
            canvas.fillRect(getX(), getY(), getWidth(), getHeight(), theme.getRadius() * 1.5f, theme.getSurface());
            canvas.strokeRect(getX(), getY(), getWidth(), getHeight(), theme.getRadius() * 1.5f, 1, theme.getBorder());
        }
        for (Widget child : children()) {
            if (!child.isShown()) {
                continue;
            }
            if (child.isEnabled()) {
                child.draw(canvas);
            } else {
                canvas.dimmed(() -> child.draw(canvas));
            }
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
        return interactive() || hoverOnly() ? this : null;
    }

    @Override
    Widget widgetAt(float px, float py) {
        if (!isShown() || !contains(px, py)) {
            return null;
        }
        List<Widget> kids = children();
        for (int i = kids.size() - 1; i >= 0; i--) {
            Widget hit = kids.get(i).widgetAt(px, py);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    /** All focusable widgets inside this container, in Tab order (declaration order, depth first). */
    final List<Widget> focusables() {
        List<Widget> result = new ArrayList<>();
        collectFocusables(result);
        return result;
    }

    private void collectFocusables(List<Widget> into) {
        for (Widget child : children()) {
            if (!child.isShown() || !child.isEnabled()) {
                continue;
            }
            if (child.focusable()) {
                into.add(child);
            }
            if (child instanceof Container container) {
                container.collectFocusables(into);
            }
        }
    }
}
