package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.Canvas;
import com.dervarex.unweird.Cursor;
import com.dervarex.unweird.KeyPress;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Size;
import com.dervarex.unweird.Widget;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A 3D view inside a normal layout: {@code new View3D(scene).height(360).autoRotate(20)}.
 *
 * <p>Mouse: left-drag orbits, right- or middle-drag pans, the wheel zooms. Keys (when focused): arrows orbit,
 * +/- zoom. A click without dragging reports what's under the mouse to {@link #onClick}.
 */
public class View3D extends Widget {

    private static final float CLICK_SLOP = 4;

    private final Scene scene;
    private final Renderer renderer = new Renderer();
    private Camera camera = Camera.orbit(0, 0, 0, 5);
    private float preferredHeight = 320;
    private boolean controls = true;
    private float autoRotate;
    private double lastTime = -1;

    private boolean dragging;
    private float dragDistance;
    private BiConsumer<Hit, Pointer.Button> clickHandler;
    private java.util.function.DoubleConsumer frameHandler;

    public View3D(Scene scene) {
        this.scene = Objects.requireNonNull(scene, "scene");
    }

    public Scene scene() {
        return scene;
    }

    public Camera camera() {
        return camera;
    }

    public View3D camera(Camera camera) {
        this.camera = Objects.requireNonNull(camera, "camera");
        return this;
    }

    /** Height in logical pixels (the width follows the layout). */
    public View3D height(float height) {
        preferredHeight = height;
        return this;
    }

    /** Turns slowly around the target, in degrees per second (0 = off). Pauses while the user drags. */
    public View3D autoRotate(float degreesPerSecond) {
        autoRotate = degreesPerSecond;
        return this;
    }

    /** Mouse and keyboard camera controls (on by default). */
    public View3D controls(boolean enabled) {
        controls = enabled;
        return this;
    }

    /** Called for a click (press and release without dragging) with what's under the mouse, or null. */
    public View3D onClick(BiConsumer<Hit, Pointer.Button> handler) {
        clickHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * Runs {@code handler} before every frame with the time in seconds, and keeps frames coming while it's set:
     * for animations, e.g. {@code onFrame(t -> arm.rotation((float) Math.sin(t * 6) * 30, 0, 0))}.
     */
    public View3D onFrame(java.util.function.DoubleConsumer handler) {
        frameHandler = handler;
        return this;
    }

    /** What's under a point of this view (logical pixels relative to its top-left), or null. */
    public Hit pick(float x, float y) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return null;
        }
        return scene.pick(camera.ray(x, y, getWidth(), getHeight()));
    }

    @Override public View3D below(Widget other) { super.below(other); return this; }
    @Override public View3D above(Widget other) { super.above(other); return this; }
    @Override public View3D rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public View3D leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public View3D alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public View3D alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public View3D gap(float pixels) { super.gap(pixels); return this; }
    @Override public View3D visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public View3D enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return this; }
    @Override public View3D tooltip(String text) { super.tooltip(text); return this; }

    // ---- layout and drawing ----

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(maxWidth, preferredHeight);
    }

    @Override
    protected void draw(Canvas canvas) {
        if (autoRotate != 0 && !dragging) {
            double now = canvas.time();
            if (lastTime >= 0) {
                camera.orbitBy((float) (autoRotate * (now - lastTime)), 0);
            }
            lastTime = now;
            requestFrame();
        } else {
            lastTime = -1;
        }
        if (frameHandler != null) {
            frameHandler.accept(canvas.time());
            requestFrame();
        }
        canvas.gl(this, getX(), getY(), getWidth(), getHeight(), canvas.getTheme().getRadius() * 1.5f,
                (width, height) -> renderer.render(canvas.openGl(), scene, camera, width, height));
        if (isFocused() && isFocusVisible()) {
            canvas.strokeRect(getX(), getY(), getWidth(), getHeight(), canvas.getTheme().getRadius() * 1.5f, 2,
                    canvas.getTheme().getAccent().withAlpha(0.6f));
        }
    }

    // ---- input ----

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected boolean focusable() {
        return true;
    }

    @Override
    protected Cursor cursor() {
        return dragging && dragDistance > CLICK_SLOP ? Cursor.MOVE : clickHandler != null ? Cursor.CROSSHAIR : Cursor.ARROW;
    }

    @Override
    protected void onPress(Pointer pointer) {
        dragging = true;
        dragDistance = 0;
    }

    @Override
    protected void onDrag(Pointer pointer) {
        dragDistance += Math.abs(pointer.dx()) + Math.abs(pointer.dy());
        if (!controls || dragDistance <= CLICK_SLOP) {
            return;
        }
        if (pointer.button() == Pointer.Button.LEFT) {
            camera.orbitBy(-pointer.dx() * 0.4f, pointer.dy() * 0.4f);
        } else {
            camera.panBy(pointer.dx() / getHeight(), pointer.dy() / getHeight());
        }
    }

    @Override
    protected void onRelease(Pointer pointer) {
        boolean click = dragDistance <= CLICK_SLOP;
        dragging = false;
        if (click && clickHandler != null && contains(pointer)) {
            clickHandler.accept(pick(pointer.x() - getX(), pointer.y() - getY()), pointer.button());
        }
    }

    private boolean contains(Pointer pointer) {
        return pointer.x() >= getX() && pointer.y() >= getY() && pointer.x() < getX() + getWidth() && pointer.y() < getY() + getHeight();
    }

    @Override
    protected boolean onScroll(Pointer at, double dx, double dy) {
        if (!controls || dy == 0) {
            return false;
        }
        camera.zoomBy(dy);
        return true;
    }

    @Override
    protected boolean onKey(KeyPress key) {
        if (!controls) {
            return false;
        }
        if (key.is("Left")) {
            camera.orbitBy(10, 0);
        } else if (key.is("Right")) {
            camera.orbitBy(-10, 0);
        } else if (key.is("Up")) {
            camera.orbitBy(0, 8);
        } else if (key.is("Down")) {
            camera.orbitBy(0, -8);
        } else if (key.is("Plus") || key.is("Shift+Plus")) {
            camera.zoomBy(1);
        } else if (key.is("Minus")) {
            camera.zoomBy(-1);
        } else {
            return false;
        }
        return true;
    }
}
