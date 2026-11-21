package com.dervarex.unweird;

/**
 * Something drawn above the whole screen, like a dropdown's list. While one is open it gets the mouse first;
 * clicking outside it or pressing Escape closes it. Opened through {@link Host#openOverlay}.
 */
abstract class Overlay extends Widget {

    /** The widget that opened this overlay; it keeps keyboard focus while the overlay is open. */
    abstract Widget owner();

    /** Sets this overlay's bounds (via {@link #place}) for a window of the given logical size. */
    abstract void position(Canvas canvas, float windowWidth, float windowHeight);

    /** Called once the overlay has been closed, whatever closed it. */
    void closed() {
    }

    /** Mouse wheel over the overlay. Returns whether it used the scroll. */
    boolean scrolled(double steps) {
        return false;
    }

    /** The mouse moved over the overlay. Returns whether anything visible changed. */
    boolean mouseMoved(float px, float py) {
        return false;
    }

    @Override
    protected boolean clickable() {
        return true;
    }
}
