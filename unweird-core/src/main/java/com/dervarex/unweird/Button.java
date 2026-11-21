package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/** A clickable button. {@code new Button("Sign in").onClick(() -> ...)} */
public class Button extends Widget {

    /** How the button looks; decided by the layout (filled in content, plain in sidebars and headers). */
    enum Variant { PRIMARY, GHOST }

    private static final float HEIGHT = 40;
    private static final float GHOST_HEIGHT = 36;
    private static final float PADDING_X = 18;
    private static final float GHOST_PADDING_X = 12;
    private static final float FONT_SIZE = 15;

    Variant variant = Variant.PRIMARY;
    /** Label at the start instead of centered (navigation items). */
    boolean alignStart;

    private final List<Runnable> handlers = new CopyOnWriteArrayList<>();
    private volatile String label;

    public Button(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /** Runs {@code handler} on every click. Several handlers run in the order they were added. */
    public Button onClick(Runnable handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    public String label() {
        return label;
    }

    public Button label(String label) {
        this.label = Objects.requireNonNull(label, "label");
        return this;
    }

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    boolean focusable() {
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
        boolean ghost = variant == Variant.GHOST;
        float padding = ghost ? GHOST_PADDING_X : PADDING_X;
        float width = canvas.textWidth(label, Canvas.Weight.SEMIBOLD, FONT_SIZE) + padding * 2;
        return new Size(Math.min(width, maxWidth), ghost ? GHOST_HEIGHT : HEIGHT);
    }

    @Override
    protected void draw(Canvas canvas) {
        var theme = canvas.getTheme();
        if (variant == Variant.GHOST) {
            drawGhost(canvas);
            return;
        }
        var fill = theme.getAccent();
        if (isPressed()) {
            fill = fill.mix(Color.BLACK, 0.15f);
        } else if (isHovered()) {
            fill = fill.mix(Color.WHITE, 0.12f);
        }
        if (isFocused() && isFocusVisible()) {
            float ring = 3;
            canvas.strokeRect(getX() - ring, getY() - ring, getWidth() + ring * 2, getHeight() + ring * 2,
                    theme.getRadius() + ring, 2, theme.getAccent().withAlpha(0.5f));
        }
        canvas.fillRect(getX(), getY(), getWidth(), getHeight(), theme.getRadius(), fill);
        canvas.label(label, getX() + getWidth() / 2, getY() + getHeight() / 2, Canvas.Weight.SEMIBOLD, FONT_SIZE, theme.getOnAccent());
    }

    private void drawGhost(Canvas canvas) {
        var theme = canvas.getTheme();
        float alpha = isPressed() ? 0.10f : isHovered() ? 0.06f : 0;
        if (alpha > 0) {
            canvas.fillRect(getX(), getY(), getWidth(), getHeight(), theme.getRadius(), theme.getText().withAlpha(alpha));
        }
        if (isFocused() && isFocusVisible()) {
            canvas.strokeRect(getX(), getY(), getWidth(), getHeight(), theme.getRadius(), 2, theme.getAccent().withAlpha(0.6f));
        }
        float centerY = getY() + getHeight() / 2;
        if (alignStart) {
            canvas.textLine(label, getX() + GHOST_PADDING_X, centerY, Canvas.Weight.SEMIBOLD, FONT_SIZE, theme.getText());
        } else {
            canvas.label(label, getX() + getWidth() / 2, centerY, Canvas.Weight.SEMIBOLD, FONT_SIZE, theme.getText());
        }
    }
}
