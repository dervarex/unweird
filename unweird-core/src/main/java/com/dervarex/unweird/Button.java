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

    private static final float HEIGHT = 40;
    private static final float PADDING_X = 18;
    private static final float FONT_SIZE = 15;

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
        float width = canvas.textWidth(label, Canvas.Weight.SEMIBOLD, FONT_SIZE) + PADDING_X * 2;
        return new Size(Math.min(width, maxWidth), HEIGHT);
    }

    @Override
    protected void draw(Canvas canvas) {
        var theme = canvas.getTheme();
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
}
