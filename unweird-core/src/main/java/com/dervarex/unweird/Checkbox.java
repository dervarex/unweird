package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;

/** A box with a label. {@code new Checkbox("Remember me")}; read it with {@link #checked()}. */
public class Checkbox extends Choice<Checkbox> {

    private static final float BOX = 18;

    public Checkbox(String label) {
        super(label);
    }

    @Override
    float controlWidth() {
        return BOX;
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        float boxX = getX();
        float boxY = getY() + (getHeight() - BOX) / 2;
        float radius = Math.min(5, theme.getRadius());

        if (checked()) {
            var fill = isPressed() ? theme.getAccent().mix(Color.BLACK, 0.15f)
                    : isHovered() ? theme.getAccent().mix(Color.WHITE, 0.12f) : theme.getAccent();
            canvas.fillRect(boxX, boxY, BOX, BOX, radius, fill);
            canvas.polyline(new float[]{
                    boxX + 4.5f, boxY + 9.5f,
                    boxX + 7.8f, boxY + 12.8f,
                    boxX + 13.5f, boxY + 5.8f}, 2, theme.getOnAccent());
        } else {
            canvas.fillRect(boxX, boxY, BOX, BOX, radius, theme.getBackground());
            canvas.strokeRect(boxX, boxY, BOX, BOX, radius, 1.5f, isHovered() ? theme.getMutedText() : theme.getMutedText().withAlpha(0.6f));
        }
        drawFocusRing(canvas, boxX, boxY, BOX, BOX, radius);
        canvas.textLine(label(), boxX + BOX + 10, getY() + getHeight() / 2, Canvas.Weight.REGULAR, FONT_SIZE, theme.getText());
    }
}
