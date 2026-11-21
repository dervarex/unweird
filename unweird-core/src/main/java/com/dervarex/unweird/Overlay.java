package com.dervarex.unweird;

import java.util.List;

/**
 * Something drawn above the whole screen: a dropdown's list (a popup) or a dialog (modal).
 * Overlays stack; the top one gets input. A popup closes when you click outside it, press Escape or move focus away.
 * A modal overlay blocks everything below it and keeps Tab inside itself.
 */
abstract class Overlay extends Widget {

    /** The widget that opened this overlay (popups keep it focused), or null. */
    abstract Widget owner();

    /** Sets this overlay's bounds (via {@link #place}) for a window of the given logical size. */
    abstract void position(Canvas canvas, float windowWidth, float windowHeight);

    /** Blocks input to everything below. */
    boolean modal() {
        return false;
    }

    /** Escape was pressed and nothing inside used it. Popups are closed by the window afterwards. */
    void escape() {
    }

    /** Where focus goes when a modal overlay opens. */
    Widget initialFocus() {
        return focusables().stream().findFirst().orElse(null);
    }

    /** Tab order inside a modal overlay. */
    List<Widget> focusables() {
        return List.of();
    }

    /** Whether {@code widget} belongs to this overlay (for cleaning up hover/focus when it closes). */
    boolean owns(Widget widget) {
        return false;
    }

    /** Called once the overlay has been closed, whatever closed it. */
    void closed() {
    }

    @Override
    protected boolean clickable() {
        return true;
    }
}
