package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/**
 * A clickable button. {@code new Button("Sign in").onClick(() -> ...)}
 *
 * <p>Its look is inferred: alone it's a filled primary button; in a row, the first is primary and the
 * others are secondary; in sidebars and headers it's a plain navigation item. {@link #primary()},
 * {@link #secondary()}, {@link #ghost()} and {@link #danger()} override that.
 */
public class Button extends Widget {

    enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }

    private static final float HEIGHT = 40;
    private static final float GHOST_HEIGHT = 36;
    private static final float PADDING_X = 18;
    private static final float GHOST_PADDING_X = 12;
    private static final float FONT_SIZE = 15;

    private final List<Runnable> handlers = new CopyOnWriteArrayList<>();
    private volatile Supplier<String> source;
    private volatile String label;

    /** What the developer asked for; null = let the layout decide. */
    Variant variant;
    /** What the layout decided. */
    Variant resolved = Variant.PRIMARY;
    /** Label at the start instead of centered (navigation items). */
    boolean alignStart;

    public Button(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /** A button whose label is re-evaluated after every event, e.g. {@code () -> dark ? "Light mode" : "Dark mode"}. */
    public Button(Supplier<String> label) {
        this.source = Objects.requireNonNull(label, "label");
        this.label = "";
    }

    /** Runs {@code handler} on every click. Several handlers run in the order they were added. */
    public Button onClick(Runnable handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    public String label() {
        return label;
    }

    /** Replaces the label (and any lambda it was created with). */
    public Button label(String label) {
        this.source = null;
        this.label = Objects.requireNonNull(label, "label");
        return this;
    }

    /** Filled with the accent color: the main action. */
    public Button primary() {
        variant = Variant.PRIMARY;
        return this;
    }

    /** Outlined: an alternative action. */
    public Button secondary() {
        variant = Variant.SECONDARY;
        return this;
    }

    /** Just the label, highlighted on hover: low-key actions and navigation. */
    public Button ghost() {
        variant = Variant.GHOST;
        return this;
    }

    /** Filled red: destructive actions. */
    public Button danger() {
        variant = Variant.DANGER;
        return this;
    }

    @Override public Button below(Widget other) { super.below(other); return this; }
    @Override public Button above(Widget other) { super.above(other); return this; }
    @Override public Button rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public Button leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public Button alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public Button alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public Button gap(float pixels) { super.gap(pixels); return this; }
    @Override public Button visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public Button enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return this; }

    @Override
    protected void update() {
        Supplier<String> current = source;
        if (current != null) {
            label = String.valueOf(current.get());
        }
    }

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected boolean focusable() {
        return true;
    }

    @Override
    boolean keyPressed(int key, int mods, Host host) {
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_SPACE) {
            host.safely(this, this::click);
            return true;
        }
        return false;
    }

    @Override
    protected void click() {
        for (Runnable handler : handlers) {
            handler.run();
        }
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        boolean ghost = resolved == Variant.GHOST;
        float padding = ghost ? GHOST_PADDING_X : PADDING_X;
        float width = canvas.textWidth(label, Canvas.Weight.SEMIBOLD, FONT_SIZE) + padding * 2;
        return new Size(Math.min(width, maxWidth), ghost ? GHOST_HEIGHT : HEIGHT);
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float radius = theme.getRadius();
        Color labelColor;

        switch (resolved) {
            case PRIMARY, DANGER -> {
                Color fill = resolved == Variant.DANGER ? theme.getDanger() : theme.getAccent();
                if (isPressed()) {
                    fill = fill.mix(Color.BLACK, 0.15f);
                } else if (isHovered()) {
                    fill = fill.mix(Color.WHITE, 0.12f);
                }
                canvas.fillRect(x, y, w, h, radius, fill);
                labelColor = resolved == Variant.DANGER ? theme.getOnDanger() : theme.getOnAccent();
            }
            case SECONDARY -> {
                float alpha = isPressed() ? 0.10f : isHovered() ? 0.05f : 0;
                if (alpha > 0) {
                    canvas.fillRect(x, y, w, h, radius, theme.getText().withAlpha(alpha));
                }
                canvas.strokeRect(x, y, w, h, radius, 1, isHovered() ? theme.getMutedText().withAlpha(0.6f) : theme.getBorder());
                labelColor = theme.getText();
            }
            default -> { // GHOST
                float alpha = isPressed() ? 0.10f : isHovered() ? 0.06f : 0;
                if (alpha > 0) {
                    canvas.fillRect(x, y, w, h, radius, theme.getText().withAlpha(alpha));
                }
                labelColor = theme.getText();
            }
        }

        if (isFocused() && isFocusVisible()) {
            float ring = resolved == Variant.GHOST ? 0 : 3;
            canvas.strokeRect(x - ring, y - ring, w + ring * 2, h + ring * 2, radius + ring, 2, theme.getAccent().withAlpha(0.55f));
        }

        float centerY = y + h / 2;
        if (alignStart) {
            canvas.textLine(label, x + GHOST_PADDING_X, centerY, Canvas.Weight.SEMIBOLD, FONT_SIZE, labelColor);
        } else {
            canvas.label(label, x + w / 2, centerY, Canvas.Weight.SEMIBOLD, FONT_SIZE, labelColor);
        }
    }
}
