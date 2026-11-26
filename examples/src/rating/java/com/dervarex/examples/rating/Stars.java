package com.dervarex.examples.rating;

import com.dervarex.unweird.Canvas;
import com.dervarex.unweird.Cursor;
import com.dervarex.unweird.KeyPress;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Size;
import com.dervarex.unweird.Widget;
import com.dervarex.unweird.style.Theme;

import java.util.function.IntConsumer;

/** A custom widget: says how big it is, draws itself, handles mouse and keyboard. */
public class Stars extends Widget {

    private static final int COUNT = 5;
    private static final float STAR = 30;
    private static final float GAP = 6;

    private int value;
    private int hovered = -1;
    private IntConsumer onChange = v -> { };

    public int value() {
        return value;
    }

    public Stars value(int stars) {
        value = Math.clamp(stars, 0, COUNT);
        onChange.accept(value);
        return this;
    }

    public Stars onChange(IntConsumer handler) {
        onChange = handler;
        return this;
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(COUNT * STAR + (COUNT - 1) * GAP, STAR);
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        int lit = isHovered() && hovered >= 0 ? hovered + 1 : value;
        for (int i = 0; i < COUNT; i++) {
            float centerX = getX() + i * (STAR + GAP) + STAR / 2;
            canvas.label("★", centerX, getY() + STAR / 2, Canvas.Weight.REGULAR, STAR, i < lit ? theme.getAccent() : theme.getBorder());
        }
        if (isFocusVisible()) { // keyboard focus only, like the built-in widgets
            canvas.strokeRect(getX() - 4, getY() - 4, getWidth() + 8, getHeight() + 8, theme.getRadius(), 2, theme.getAccent());
        }
    }

    @Override protected boolean clickable() { return true; }
    @Override protected boolean focusable() { return true; }
    @Override protected Cursor cursor() { return Cursor.HAND; }

    @Override
    protected boolean onMove(Pointer pointer) {
        int next = starAt(pointer.x());
        boolean changed = next != hovered;
        hovered = next;
        return changed; // true: redraw
    }

    @Override
    protected void onPress(Pointer pointer) {
        int star = starAt(pointer.x()) + 1;
        value(star == value ? 0 : star); // clicking the current rating clears it
    }

    @Override
    protected boolean onKey(KeyPress key) {
        if (key.is("Left")) value(value - 1);
        else if (key.is("Right")) value(value + 1);
        else if (key.is("Home")) value(0);
        else if (key.is("End")) value(COUNT);
        else return false; // not ours: let shortcuts have it
        return true;
    }

    private int starAt(float x) {
        return (int) Math.clamp((x - getX()) / (STAR + GAP), 0, COUNT - 1);
    }
}
