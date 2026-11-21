package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;

/**
 * An on/off switch with a label, for settings that apply immediately.
 * {@code new Toggle("Dark mode").onChange(on -> ...)}. The label is on the left, the switch on the right.
 */
public class Toggle extends Choice<Toggle> {

    private static final float TRACK_WIDTH = 38;
    private static final float TRACK_HEIGHT = 22;

    public Toggle(String label) {
        super(label);
    }

    @Override
    float controlWidth() {
        return TRACK_WIDTH;
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        float centerY = getY() + getHeight() / 2;
        float trackX = getX() + getWidth() - TRACK_WIDTH;
        float trackY = centerY - TRACK_HEIGHT / 2;
        float radius = TRACK_HEIGHT / 2;

        canvas.textLine(label(), getX(), centerY, Canvas.Weight.REGULAR, FONT_SIZE, theme.getText());

        Color track = checked() ? theme.getAccent() : theme.getMutedText().withAlpha(0.35f);
        if (isHovered()) {
            track = track.mix(checked() ? Color.WHITE : theme.getText(), 0.12f);
        }
        canvas.fillRect(trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, radius, track);
        float knobRadius = radius - 3;
        float knobX = checked() ? trackX + TRACK_WIDTH - radius : trackX + radius;
        canvas.circle(knobX, centerY, knobRadius, Color.WHITE);
        drawFocusRing(canvas, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, radius);
    }
}
