package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Pick a number by dragging. {@code new Slider("Volume", 0, 100).value(30).onChange(v -> ...)}.
 * Arrow keys step, Page Up/Down jump 10 steps, Home/End go to the ends; the mouse wheel works while focused.
 *
 * <p>Built only on the public widget hooks ({@code onPress}, {@code onDrag}, {@code onKey}, {@code onScroll}),
 * so it doubles as an example for writing your own widgets.
 */
public class Slider extends Widget {

    private static final float HEIGHT = 46;
    private static final float TRACK = 4;
    private static final float KNOB = 9;
    private static final float FONT_SIZE = 15;

    private final String label;
    private final double min;
    private final double max;
    private final List<DoubleConsumer> handlers = new CopyOnWriteArrayList<>();
    private double step = 1;
    private volatile double value;
    private DoubleFunction<String> format = v -> step >= 1 ? Long.toString(Math.round(v)) : String.format("%.1f", v);

    public Slider(String label, double min, double max) {
        if (!(max > min)) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        this.label = Objects.requireNonNull(label, "label");
        this.min = min;
        this.max = max;
        this.value = min;
    }

    public double value() {
        return value;
    }

    /** Sets the value (clamped and snapped to the step) without calling the handlers. */
    public Slider value(double next) {
        value = snap(next);
        return this;
    }

    /** Values snap to multiples of {@code step} from {@code min}; 0 means no snapping. */
    public Slider step(double step) {
        if (step < 0) {
            throw new IllegalArgumentException("step must be zero or positive");
        }
        this.step = step;
        value = snap(value);
        return this;
    }

    /** How the value is shown, e.g. {@code v -> Math.round(v) + "%"}. */
    public Slider format(DoubleFunction<String> format) {
        this.format = Objects.requireNonNull(format, "format");
        return this;
    }

    /** Runs {@code handler} with the new value whenever the user changes it. */
    public Slider onChange(DoubleConsumer handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override public Slider below(Widget other) { super.below(other); return this; }
    @Override public Slider above(Widget other) { super.above(other); return this; }
    @Override public Slider rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public Slider leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public Slider alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public Slider alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public Slider gap(float pixels) { super.gap(pixels); return this; }
    @Override public Slider visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public Slider enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return this; }
    @Override public Slider tooltip(String text) { super.tooltip(text); return this; }

    private double snap(double raw) {
        double clamped = Math.max(min, Math.min(max, raw));
        if (step <= 0) {
            return clamped;
        }
        return Math.max(min, Math.min(max, min + Math.round((clamped - min) / step) * step));
    }

    private void change(double next) {
        double snapped = snap(next);
        if (snapped == value) {
            return;
        }
        value = snapped;
        for (DoubleConsumer handler : handlers) {
            handler.accept(snapped);
        }
    }

    private double stepSize() {
        return step > 0 ? step : (max - min) / 100;
    }

    // ---- input: public hooks only ----

    @Override
    protected boolean focusable() {
        return true;
    }

    @Override
    protected Cursor cursor() {
        return Cursor.HAND;
    }

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected void onPress(Pointer pointer) {
        if (pointer.button() == Pointer.Button.LEFT) {
            change(valueAt(pointer.x()));
        }
    }

    @Override
    protected void onDrag(Pointer pointer) {
        if (pointer.button() == Pointer.Button.LEFT) {
            change(valueAt(pointer.x()));
        }
    }

    @Override
    protected boolean onKey(KeyPress key) {
        if (key.is("Left") || key.is("Down")) {
            change(value - stepSize());
        } else if (key.is("Right") || key.is("Up")) {
            change(value + stepSize());
        } else if (key.is("PageDown")) {
            change(value - stepSize() * 10);
        } else if (key.is("PageUp")) {
            change(value + stepSize() * 10);
        } else if (key.is("Home")) {
            change(min);
        } else if (key.is("End")) {
            change(max);
        } else {
            return false;
        }
        return true;
    }

    @Override
    protected boolean onScroll(Pointer at, double dx, double dy) {
        if (!isFocused() || dy == 0) {
            return false; // the page scrolls unless you're working with the slider
        }
        change(value + Math.signum(dy) * stepSize());
        return true;
    }

    private double valueAt(float x) {
        float left = getX() + KNOB;
        float width = Math.max(1, getWidth() - KNOB * 2);
        return min + (max - min) * Math.max(0, Math.min(1, (x - left) / width));
    }

    // ---- drawing ----

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(Math.min(240, maxWidth), HEIGHT);
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        float x = getX();
        float y = getY();
        float w = getWidth();
        float labelY = y + 10;
        canvas.textLine(label, x, labelY, Canvas.Weight.REGULAR, FONT_SIZE, theme.getText());
        String shown = format.apply(value);
        float valueWidth = canvas.textWidth(shown, Canvas.Weight.SEMIBOLD, FONT_SIZE);
        canvas.textLine(shown, x + w - valueWidth, labelY, Canvas.Weight.SEMIBOLD, FONT_SIZE, theme.getMutedText());

        float trackY = y + HEIGHT - KNOB - 2;
        float left = x + KNOB;
        float width = Math.max(1, w - KNOB * 2);
        float fraction = (float) ((value - min) / (max - min));
        canvas.fillRect(left, trackY - TRACK / 2, width, TRACK, TRACK / 2, theme.getMutedText().withAlpha(0.25f));
        canvas.fillRect(left, trackY - TRACK / 2, width * fraction, TRACK, TRACK / 2, theme.getAccent());

        float knobX = left + width * fraction;
        if (isFocused() && isFocusVisible()) {
            canvas.circle(knobX, trackY, KNOB + 4, theme.getAccent().withAlpha(0.3f));
        }
        canvas.circle(knobX, trackY, KNOB, isPressed() ? theme.getAccent().mix(Color.BLACK, 0.15f) : theme.getAccent());
        canvas.circle(knobX, trackY, KNOB - 3, Color.WHITE);
    }
}
