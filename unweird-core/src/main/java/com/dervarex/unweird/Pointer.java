package com.dervarex.unweird;

/**
 * A mouse event for custom widgets. Coordinates are logical pixels in window space; subtract the widget's
 * {@code getX()/getY()} for local ones.
 *
 * @param clicks quick successive clicks so far (2 = double click); 0 for moves and drags
 * @param dx     for drags: movement since the last event
 * @param dy     for drags: movement since the last event
 */
public record Pointer(float x, float y, Button button, int clicks, boolean shift, boolean ctrl, boolean alt, float dx, float dy) {

    public enum Button { LEFT, RIGHT, MIDDLE, NONE }
}
