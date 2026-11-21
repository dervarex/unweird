package com.dervarex.unweird;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * Anything that can be shown: {@link Text}, {@link Button}, {@link Field}, and containers such as
 * {@link Screen} and {@link Component}.
 *
 * <p>All coordinates are logical pixels; the window maps them to physical pixels for HiDPI displays.
 */
public abstract class Widget {

    enum CursorShape { ARROW, HAND, IBEAM }

    @Getter(AccessLevel.PROTECTED) private float x;
    @Getter(AccessLevel.PROTECTED) private float y;
    @Getter(AccessLevel.PROTECTED) private float width;
    @Getter(AccessLevel.PROTECTED) private float height;

    /** The mouse is over this widget (only tracked for interactive widgets). */
    @Getter(AccessLevel.PROTECTED) boolean hovered;
    /** The mouse button went down on this widget and hasn't been released yet. */
    @Getter(AccessLevel.PROTECTED) boolean pressed;
    /** This widget receives keyboard input. */
    @Getter(AccessLevel.PROTECTED) boolean focused;
    /** Focus arrived via the keyboard (Tab), so a focus ring should be visible. */
    @Getter(AccessLevel.PROTECTED) boolean focusVisible;
    /** Blink phase of the text caret, driven by the window. */
    boolean caretOn = true;

    Container parent;

    /** Re-evaluates lambdas such as {@code new Text(() -> ...)}. Runs before every layout. */
    protected void update() {
    }

    /** The size this widget would like, given at most {@code maxWidth} horizontally. */
    protected abstract Size measure(Canvas canvas, float maxWidth);

    /** Positions children after this widget's own bounds were set. Only containers need this. */
    protected void arrange(Canvas canvas) {
    }

    protected abstract void draw(Canvas canvas);

    /** Whether a mouse click (press + release on the widget) calls {@link #click()}. */
    protected boolean clickable() {
        return false;
    }

    /** Called when the widget was clicked, or activated with the keyboard. */
    protected void click() {
    }

    // ---- input; package-private until custom-widget input gets a public API ----

    boolean focusable() {
        return false;
    }

    /** Handles a key press (or repeat). Returns whether the key was used. Keys are GLFW key codes. */
    boolean keyPressed(int key, int mods, Host host) {
        return false;
    }

    /** A typed character (already translated by the keyboard layout). */
    void typed(int codePoint, Host host) {
    }

    /** Mouse went down on this widget. {@code clicks} counts quick successive clicks (2 = double click). */
    void mouseDown(float px, float py, int clicks, boolean shift) {
    }

    /** Mouse moved while the button is held after {@link #mouseDown}. */
    void mouseDrag(float px, float py) {
    }

    /** Whether the window should blink a caret while this widget is focused. */
    boolean blinks() {
        return false;
    }

    CursorShape cursorShape() {
        return CursorShape.HAND;
    }

    final boolean interactive() {
        return clickable() || focusable();
    }

    final void place(Canvas canvas, float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        arrange(canvas);
    }

    /** The innermost interactive widget at the given point, or {@code null}. */
    Widget hitTest(float px, float py) {
        return interactive() && contains(px, py) ? this : null;
    }

    final boolean contains(float px, float py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
}
