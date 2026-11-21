package com.dervarex.unweird;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * Anything that can be shown: {@link Text}, {@link Button}, and containers such as {@link Screen} and {@link Component}.
 *
 * <p>All coordinates are logical pixels; the window maps them to physical pixels for HiDPI displays.
 */
public abstract class Widget {

    @Getter(AccessLevel.PROTECTED) private float x;
    @Getter(AccessLevel.PROTECTED) private float y;
    @Getter(AccessLevel.PROTECTED) private float width;
    @Getter(AccessLevel.PROTECTED) private float height;

    /** The mouse is over this widget (only tracked for {@link #clickable()} widgets). */
    @Getter(AccessLevel.PROTECTED) boolean hovered;
    /** The mouse button went down on this widget and hasn't been released yet. */
    @Getter(AccessLevel.PROTECTED) boolean pressed;

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

    /** Whether this widget reacts to the mouse (gets hover/press state and {@link #click()}). */
    protected boolean clickable() {
        return false;
    }

    /** Called when the mouse was pressed and released on this widget. */
    protected void click() {
    }

    final void place(Canvas canvas, float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        arrange(canvas);
    }

    /** The innermost clickable widget at the given point, or {@code null}. */
    Widget hitTest(float px, float py) {
        return clickable() && contains(px, py) ? this : null;
    }

    final boolean contains(float px, float py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
}
