package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGGlyphPosition;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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
    /** Loaded images by source; -1 marks a source that failed to load (reported once). */
    private final Map<String, Integer> images = new HashMap<>();

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

    /** Draws a single line of text starting at {@code x}, vertically centered on {@code centerY}. */
    public void textLine(String text, float x, float centerY, Weight weight, float size, Color fill) {
        if (text.isEmpty()) {
            return;
        }
        font(weight, size, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, nvg(fill, color));
        nvgText(vg, x, centerY, text);
    }

    /** Height of one line of text at the given size. */
    public float lineHeight(float size) {
        return size * LINE_HEIGHT;
    }

    /**
     * For a single line of {@code text} drawn at x = 0: the x position of the caret before each char index,
     * plus one at the end. The result has {@code text.length() + 1} entries.
     * Indices inside a surrogate pair get the position of the pair's start.
     */
    public float[] caretPositions(String text, Weight weight, float size) {
        float[] xs = new float[text.length() + 1];
        if (text.isEmpty()) {
            return xs;
        }
        font(weight, size, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);

        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        int[] charAtByte = new int[utf8.length + 1];
        for (int i = 0, b = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int bytes = utf8Length(cp);
            Arrays.fill(charAtByte, b, b + bytes, i);
            b += bytes;
            i += Character.charCount(cp);
        }
        charAtByte[utf8.length] = text.length();

        Arrays.fill(xs, Float.NaN);
        ByteBuffer buffer = MemoryUtil.memAlloc(utf8.length);
        NVGGlyphPosition.Buffer glyphs = NVGGlyphPosition.malloc(text.codePointCount(0, text.length()));
        try {
            buffer.put(utf8).flip();
            long start = MemoryUtil.memAddress(buffer);
            int count = nvgTextGlyphPositions(vg, 0, 0, buffer, glyphs);
            float end = 0;
            for (int g = 0; g < count; g++) {
                NVGGlyphPosition glyph = glyphs.get(g);
                int index = charAtByte[(int) (glyph.str() - start)];
                if (Float.isNaN(xs[index])) {
                    xs[index] = glyph.x();
                }
                end = Math.max(end, glyph.maxx());
            }
            xs[0] = Float.isNaN(xs[0]) ? 0 : xs[0];
            xs[text.length()] = end;
            for (int i = 1; i < text.length(); i++) {
                if (Float.isNaN(xs[i])) {
                    xs[i] = xs[i - 1]; // second half of a surrogate pair, or a character without a glyph
                }
            }
        } finally {
            glyphs.free();
            MemoryUtil.memFree(buffer);
        }
        return xs;
    }

    /** A filled circle. */
    public void circle(float centerX, float centerY, float radius, Color fill) {
        nvgBeginPath(vg);
        nvgCircle(vg, centerX, centerY, radius);
        nvgFillColor(vg, nvg(fill, color));
        nvgFill(vg);
    }

    /** An open line through the given points {x0, y0, x1, y1, ...}, with round joins and caps. */
    public void polyline(float[] points, float strokeWidth, Color stroke) {
        if (points.length < 4) {
            return;
        }
        nvgBeginPath(vg);
        nvgMoveTo(vg, points[0], points[1]);
        for (int i = 2; i + 1 < points.length; i += 2) {
            nvgLineTo(vg, points[i], points[i + 1]);
        }
        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
        nvgStrokeWidth(vg, strokeWidth);
        nvgStrokeColor(vg, nvg(stroke, color));
        nvgStroke(vg);
    }

    /**
     * The renderer's handle for an image, loading it on first use. {@code bytes} supplies the encoded file
     * (PNG, JPEG, ...) and is only called once per {@code source}. Returns -1 if it can't be loaded.
     */
    int image(String source, Supplier<byte[]> bytes) {
        Integer known = images.get(source);
        if (known != null) {
            return known;
        }
        int handle = -1;
        try {
            byte[] data = bytes.get();
            ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
            try {
                buffer.put(data).flip();
                handle = nvgCreateImageMem(vg, 0, buffer);
            } finally {
                MemoryUtil.memFree(buffer); // NanoVG has uploaded the decoded pixels by now
            }
            if (handle <= 0) {
                handle = -1;
                System.err.println("unweird: can't decode image " + source + " (PNG, JPEG, BMP, GIF and TGA work)");
            }
        } catch (RuntimeException e) {
            System.err.println("unweird: can't load image " + source + ": " + e.getMessage());
        }
        images.put(source, handle);
        return handle;
    }

    /** Pixel size of a loaded image, {width, height}. */
    int[] imageSize(int handle) {
        int[] w = new int[1];
        int[] h = new int[1];
        nvgImageSize(vg, handle, w, h);
        return new int[]{w[0], h[0]};
    }

    void drawImage(int handle, float x, float y, float width, float height, float radius) {
        nvgImagePattern(vg, x, y, width, height, 0, handle, 1, paint);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, radius);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    /** Restricts drawing to the given rectangle until {@link #unclip()}. Calls must be balanced. */
    public void clip(float x, float y, float width, float height) {
        nvgSave(vg);
        nvgIntersectScissor(vg, x, y, width, height);
    }

    public void unclip() {
        nvgRestore(vg);
    }

    void free() {
        for (int handle : images.values()) {
            if (handle > 0) {
                nvgDeleteImage(vg, handle);
            }
        }
        images.clear();
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

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80 || Character.isSurrogate((char) codePoint) && codePoint < 0x10000) return 1; // lone surrogate → '?'
        if (codePoint < 0x800) return 2;
        if (codePoint < 0x10000) return 3;
        return 4;
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
