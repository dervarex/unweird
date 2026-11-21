package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * What widgets draw on. Wraps the renderer so widget code never touches NanoVG directly.
 * All values are logical pixels.
 */
public final class Canvas {

    public enum Weight { REGULAR, SEMIBOLD }

    public enum Align { LEFT, CENTER, RIGHT }

    private static final float LINE_HEIGHT = 1.35f;
    private static final float UNBOUNDED = 1_000_000f;

    private final long vg;
    private final int regularFont;
    private final int semiboldFont;

    private final NVGColor color = NVGColor.malloc();
    private final NVGColor color2 = NVGColor.malloc();
    private final NVGPaint paint = NVGPaint.malloc();
    private final float[] bounds = new float[4];

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private Theme theme;

    Canvas(long vg, int regularFont, int semiboldFont, Theme theme) {
        this.vg = vg;
        this.regularFont = regularFont;
        this.semiboldFont = semiboldFont;
        this.theme = theme;
    }

    public void fillRect(float x, float y, float width, float height, float radius, Color fill) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, radius);
        nvgFillColor(vg, nvg(fill, color));
        nvgFill(vg);
    }

    /** Draws an outline fully inside the given rectangle. */
    public void strokeRect(float x, float y, float width, float height, float radius, float strokeWidth, Color stroke) {
        float half = strokeWidth / 2;
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x + half, y + half, width - strokeWidth, height - strokeWidth, Math.max(0, radius - half));
        nvgStrokeWidth(vg, strokeWidth);
        nvgStrokeColor(vg, nvg(stroke, color));
        nvgStroke(vg);
    }

    /** A soft drop shadow around (and slightly below) the given rectangle. */
    public void shadow(float x, float y, float width, float height, float radius, float blur, Color shade) {
        float offsetY = blur / 4;
        nvgBoxGradient(vg, x, y + offsetY, width, height, radius, blur,
                nvg(shade, color), nvg(Color.TRANSPARENT, color2), paint);
        nvgBeginPath(vg);
        nvgRect(vg, x - blur, y - blur + offsetY, width + blur * 2, height + blur * 2);
        nvgRoundedRect(vg, x, y, width, height, radius);
        nvgPathWinding(vg, NVG_HOLE);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    /** Size of {@code text} wrapped at {@code maxWidth}. */
    public Size measureText(String text, Weight weight, float size, float maxWidth) {
        font(weight, size, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        if (text.isEmpty()) {
            return new Size(0, (float) Math.ceil(size * LINE_HEIGHT));
        }
        nvgTextBoxBounds(vg, 0, 0, Math.max(1, maxWidth), text, bounds);
        // +1 so drawing at exactly the measured width never wraps because of float rounding
        float width = (float) Math.ceil(bounds[2] - bounds[0]) + 1;
        return new Size(Math.min(width, maxWidth), (float) Math.ceil(bounds[3] - bounds[1]));
    }

    /** Width of {@code text} on a single line. */
    public float textWidth(String text, Weight weight, float size) {
        font(weight, size, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        return (float) Math.ceil(nvgTextBounds(vg, 0, 0, text, bounds));
    }

    /** Draws {@code text} with its top-left at (x, y), wrapped at {@code width} and aligned within it. */
    public void text(String text, float x, float y, float width, Weight weight, float size, Color fill, Align align) {
        if (text.isEmpty()) {
            return;
        }
        font(weight, size, nvgAlign(align) | NVG_ALIGN_TOP);
        nvgFillColor(vg, nvg(fill, color));
        nvgTextBox(vg, x, y, width <= 0 ? UNBOUNDED : width, text);
    }

    /** Draws a single line of text centered on (centerX, centerY). */
    public void label(String text, float centerX, float centerY, Weight weight, float size, Color fill) {
        font(weight, size, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, nvg(fill, color));
        nvgText(vg, centerX, centerY, text);
    }

    void free() {
        color.free();
        color2.free();
        paint.free();
    }

    private void font(Weight weight, float size, int align) {
        nvgFontFaceId(vg, weight == Weight.SEMIBOLD ? semiboldFont : regularFont);
        nvgFontSize(vg, size);
        nvgTextLineHeight(vg, LINE_HEIGHT);
        nvgTextAlign(vg, align);
    }

    private static int nvgAlign(Align align) {
        return switch (align) {
            case LEFT -> NVG_ALIGN_LEFT;
            case CENTER -> NVG_ALIGN_CENTER;
            case RIGHT -> NVG_ALIGN_RIGHT;
        };
    }

    private static NVGColor nvg(Color c, NVGColor target) {
        return nvgRGBAf(c.r(), c.g(), c.b(), c.a(), target);
    }
}
