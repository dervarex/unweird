package com.dervarex.unweird;

/** A default component docked on one side of a screen, plus the panel it was given by {@link Docking}. */
final class DockSlot {

    final Component component;
    final Side side;
    float x;
    float y;
    float width;
    float height;

    DockSlot(Component component, Side side) {
        this.component = component;
        this.side = side;
    }
}
