package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/** Shared behavior of {@link Checkbox} and {@link Toggle}: an on/off value with a label, flipped by click or Space. */
abstract class Choice<T extends Choice<T>> extends Widget {

    static final float FONT_SIZE = 15;
    static final float HEIGHT = 28;

    private final String label;
    private final List<Consumer<Boolean>> handlers = new CopyOnWriteArrayList<>();
    private volatile boolean checked;

    Choice(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    public String label() {
        return label;
    }

    /** Whether it's on. */
    public boolean checked() {
        return checked;
    }

    /** Sets the value without calling the change handlers. */
    public T checked(boolean value) {
        checked = value;
        return self();
    }

    /** Runs {@code handler} with the new value whenever the user flips it. */
    public T onChange(Consumer<Boolean> handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
        return self();
    }

    @Override public T below(Widget other) { super.below(other); return self(); }
    @Override public T above(Widget other) { super.above(other); return self(); }
    @Override public T rightOf(Widget other) { super.rightOf(other); return self(); }
    @Override public T leftOf(Widget other) { super.leftOf(other); return self(); }
    @Override public T alignLeft(Widget other) { super.alignLeft(other); return self(); }
    @Override public T alignRight(Widget other) { super.alignRight(other); return self(); }
    @Override public T gap(float pixels) { super.gap(pixels); return self(); }
    @Override public T visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return self(); }
    @Override public T enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return self(); }

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected void click() {
        checked = !checked;
        boolean value = checked;
        for (Consumer<Boolean> handler : handlers) {
            handler.accept(value);
        }
    }

    @Override
    boolean focusable() {
        return true;
    }

    @Override
    boolean keyPressed(int key, int mods, Host host) {
        if (key == GLFW_KEY_SPACE) {
            host.safely(this, this::click);
            return true;
        }
        return false;
    }

    /** Width of the control itself (box or switch), without the label. */
    abstract float controlWidth();

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        float width = controlWidth() + 10 + canvas.textWidth(label, Canvas.Weight.REGULAR, FONT_SIZE);
        return new Size(Math.min(width, maxWidth), HEIGHT);
    }

    void drawFocusRing(Canvas canvas, float x, float y, float w, float h, float radius) {
        if (isFocused() && isFocusVisible()) {
            Theme theme = canvas.getTheme();
            canvas.strokeRect(x - 3, y - 3, w + 6, h + 6, radius + 3, 2, theme.getAccent().withAlpha(0.55f));
        }
    }
}
