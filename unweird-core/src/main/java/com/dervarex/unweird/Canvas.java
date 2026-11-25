package com.dervarex.unweird;

import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What widgets draw on. Wraps the renderer so widget code never touches NanoVG directly.
 * All values are logical pixels.
 */
public final class Canvas {

    public enum Weight { REGULAR, SEMIBOLD }

    public enum Align { LEFT, CENTER, RIGHT }

    private static final float LINE_HEIGHT = 1.35f;
    private static final float UNBOUNDED = 1_000_000f;

    private final Vg vg;
    private final int regularFont;
    private final int semiboldFont;

    /** Loaded images by source; -1 marks a source that failed to load (reported once). */
    private final Map<String, Integer> images = new HashMap<>();

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private Theme theme;

    /** Physical pixels per logical pixel in this frame. */
    @Getter
    private float pixelRatio = 1;
    /** Seconds since the app started, at the start of this frame. For animations. */
    private double time;
    private int framebufferWidth;
    private int framebufferHeight;
    private long frame;

    void beginFrame(float pixelRatio, double time, int framebufferWidth, int framebufferHeight) {
        this.pixelRatio = pixelRatio;
        this.time = time;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        frame++;
    }

    /** Seconds since the app started, at the start of this frame. Use it for animations (with {@link Widget#requestFrame()}). */
    public double time() {
        return time;
    }

    Vg vg() {
        return vg;
    }

    Canvas(Vg vg, int regularFont, int semiboldFont, Theme theme) {
        this.vg = vg;
        this.regularFont = regularFont;
        this.semiboldFont = semiboldFont;
        this.theme = theme;
    }

    public void fillRect(float x, float y, float width, float height, float radius, Color fill) {
        vg.beginPath();
        vg.roundedRect(x, y, width, height, radius);
        fillColor(fill);
        vg.fill();
    }

    /** Draws an outline fully inside the given rectangle. */
    public void strokeRect(float x, float y, float width, float height, float radius, float strokeWidth, Color stroke) {
        float half = strokeWidth / 2;
        vg.beginPath();
        vg.roundedRect(x + half, y + half, width - strokeWidth, height - strokeWidth, Math.max(0, radius - half));
        vg.strokeWidth(strokeWidth);
        vg.strokeColor(stroke.r(), stroke.g(), stroke.b(), stroke.a());
        vg.stroke();
    }

    /** A soft drop shadow around (and slightly below) the given rectangle. */
    public void shadow(float x, float y, float width, float height, float radius, float blur, Color shade) {
        float offsetY = blur / 4;
        Color clear = Color.TRANSPARENT;
        vg.boxGradient(x, y + offsetY, width, height, radius, blur,
                shade.r(), shade.g(), shade.b(), shade.a(), clear.r(), clear.g(), clear.b(), clear.a());
        vg.beginPath();
        vg.rect(x - blur, y - blur + offsetY, width + blur * 2, height + blur * 2);
        vg.roundedRect(x, y, width, height, radius);
        vg.hole();
        vg.fill();
    }

    /** Size of {@code text} wrapped at {@code maxWidth}. */
    public Size measureText(String text, Weight weight, float size, float maxWidth) {
        return measureText(text, weight, size, maxWidth, 0);
    }

    /** Size of {@code text} wrapped at {@code maxWidth}, cut to {@code maxLines} lines (0 = no limit). */
    public Size measureText(String text, Weight weight, float size, float maxWidth, int maxLines) {
        List<Line> lines = lines(text, weight, size, maxWidth, maxLines);
        float width = 0;
        for (Line line : lines) {
            width = Math.max(width, line.width());
        }
        // +1 so drawing at exactly the measured width never wraps because of float rounding
        return new Size(Math.min((float) Math.ceil(width) + 1, Math.max(0, maxWidth)), (float) Math.ceil(lines.size() * size * LINE_HEIGHT));
    }

    /** Width of {@code text} on a single line. */
    public float textWidth(String text, Weight weight, float size) {
        font(weight, size, Vg.ALIGN_LEFT | Vg.ALIGN_TOP);
        return (float) Math.ceil(advance(text));
    }

    /** Draws {@code text} with its top-left at (x, y), wrapped at {@code width} and aligned within it. */
    public void text(String text, float x, float y, float width, Weight weight, float size, Color fill, Align align) {
        text(text, x, y, width, weight, size, fill, align, 0);
    }

    /** Like {@link #text(String, float, float, float, Weight, float, Color, Align)}, cut to {@code maxLines} lines with "…". */
    public void text(String text, float x, float y, float width, Weight weight, float size, Color fill, Align align, int maxLines) {
        if (text.isEmpty()) {
            return;
        }
        List<Line> lines = lines(text, weight, size, width <= 0 ? UNBOUNDED : width, maxLines);
        font(weight, size, vgAlign(align) | Vg.ALIGN_TOP);
        fillColor(fill);
        float lineHeight = size * LINE_HEIGHT;
        float anchorX = switch (align) {
            case LEFT -> x;
            case CENTER -> x + width / 2;
            case RIGHT -> x + width;
        };
        for (int i = 0; i < lines.size(); i++) {
            vg.text(anchorX, y + i * lineHeight, lines.get(i).text());
        }
    }

    /** One wrapped line and its width. */
    record Line(String text, float width) {
    }

    private record WrapKey(String text, Weight weight, float size, float width, int maxLines) {
    }

    /** Recent wraps; measuring and drawing ask for the same ones every frame. */
    private final Map<WrapKey, List<Line>> wraps = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WrapKey, List<Line>> eldest) {
            return size() > 512;
        }
    };

    /** Greedy word wrap: breaks at spaces, breaks words that don't fit on their own, keeps '\n' breaks. */
    List<Line> lines(String text, Weight weight, float size, float maxWidth, int maxLines) {
        WrapKey key = new WrapKey(text, weight, size, maxWidth, maxLines);
        List<Line> cached = wraps.get(key);
        if (cached != null) {
            return cached;
        }
        font(weight, size, Vg.ALIGN_LEFT | Vg.ALIGN_TOP);
        float limit = Math.max(1, maxWidth);
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String token : paragraph.split("(?<=\\s)")) {
                if (token.isEmpty()) {
                    continue;
                }
                String candidate = line + token;
                if (advance(candidate.stripTrailing()) <= limit) {
                    line.append(token);
                    continue;
                }
                if (!line.isEmpty()) {
                    out.add(line.toString().stripTrailing());
                    line.setLength(0);
                }
                // the token alone may still be too wide: break it by characters
                String rest = token;
                while (advance(rest.stripTrailing()) > limit) {
                    int cut = fitting(rest, limit);
                    out.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            out.add(line.toString().stripTrailing());
        }
        List<Line> lines = new ArrayList<>();
        boolean cut = maxLines > 0 && out.size() > maxLines;
        int count = cut ? maxLines : out.size();
        for (int i = 0; i < count; i++) {
            String content = cut && i == count - 1 ? ellipsize(out.get(i), limit) : out.get(i);
            lines.add(new Line(content, advance(content)));
        }
        List<Line> result = List.copyOf(lines);
        wraps.put(key, result);
        return result;
    }

    /** How many chars of {@code text} fit into {@code limit} (at least one code point). */
    private int fitting(String text, float limit) {
        int end = text.offsetByCodePoints(0, 1);
        while (end < text.length()) {
            int next = text.offsetByCodePoints(end, 1);
            if (advance(text.substring(0, next)) > limit) {
                break;
            }
            end = next;
        }
        return end;
    }

    private String ellipsize(String line, float limit) {
        String shortened = line;
        while (!shortened.isEmpty() && advance(shortened.stripTrailing() + "…") > limit) {
            shortened = shortened.substring(0, shortened.offsetByCodePoints(shortened.length(), -1));
        }
        return shortened.stripTrailing() + "…";
    }

    private float advance(String text) {
        return text.isEmpty() ? 0 : vg.textAdvance(text);
    }

    /** Draws a single line of text centered on (centerX, centerY). */
    public void label(String text, float centerX, float centerY, Weight weight, float size, Color fill) {
        font(weight, size, Vg.ALIGN_CENTER | Vg.ALIGN_MIDDLE);
        fillColor(fill);
        vg.text(centerX, centerY, text);
    }

    /** Draws a single line of text starting at {@code x}, vertically centered on {@code centerY}. */
    public void textLine(String text, float x, float centerY, Weight weight, float size, Color fill) {
        if (text.isEmpty()) {
            return;
        }
        font(weight, size, Vg.ALIGN_LEFT | Vg.ALIGN_MIDDLE);
        fillColor(fill);
        vg.text(x, centerY, text);
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
        font(weight, size, Vg.ALIGN_LEFT | Vg.ALIGN_TOP);

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
        int glyphCount = text.codePointCount(0, text.length());
        int[] offsets = new int[glyphCount];
        float[] glyphXs = new float[glyphCount];
        float[] glyphEnds = new float[glyphCount];
        int count = vg.glyphPositions(utf8, offsets, glyphXs, glyphEnds);
        float end = 0;
        for (int g = 0; g < count; g++) {
            int index = charAtByte[offsets[g]];
            if (Float.isNaN(xs[index])) {
                xs[index] = glyphXs[g];
            }
            end = Math.max(end, glyphEnds[g]);
        }
        xs[0] = Float.isNaN(xs[0]) ? 0 : xs[0];
        xs[text.length()] = end;
        for (int i = 1; i < text.length(); i++) {
            if (Float.isNaN(xs[i])) {
                xs[i] = xs[i - 1]; // second half of a surrogate pair, or a character without a glyph
            }
        }
        return xs;
    }

    /** A filled circle. */
    public void circle(float centerX, float centerY, float radius, Color fill) {
        vg.beginPath();
        vg.circle(centerX, centerY, radius);
        fillColor(fill);
        vg.fill();
    }

    /** An open line through the given points {x0, y0, x1, y1, ...}, with round joins and caps. */
    public void polyline(float[] points, float strokeWidth, Color stroke) {
        if (points.length < 4) {
            return;
        }
        vg.beginPath();
        vg.moveTo(points[0], points[1]);
        for (int i = 2; i + 1 < points.length; i += 2) {
            vg.lineTo(points[i], points[i + 1]);
        }
        vg.roundLines();
        vg.strokeWidth(strokeWidth);
        vg.strokeColor(stroke.r(), stroke.g(), stroke.b(), stroke.a());
        vg.stroke();
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
            handle = vg.createImage(bytes.get());
            if (handle <= 0) {
                handle = -1;
                System.err.println("unweird: can't decode image " + source
                        + " (PNG, JPEG, BMP, GIF and TGA work; other formats need an ImageIO plugin, e.g. TwelveMonkeys for WebP)");
            }
        } catch (RuntimeException e) {
            System.err.println("unweird: can't load image " + source + ": " + e.getMessage());
        }
        images.put(source, handle);
        return handle;
    }

    /** Pixel size of a loaded image, {width, height}. */
    int[] imageSize(int handle) {
        return vg.imageSize(handle);
    }

    void drawImage(int handle, float x, float y, float width, float height, float radius) {
        vg.imagePattern(x, y, width, height, handle, 1);
        vg.beginPath();
        vg.roundedRect(x, y, width, height, radius);
        vg.fill();
    }

    /** Draws whatever {@code body} draws at reduced opacity (disabled widgets). */
    public void dimmed(Runnable body) {
        vg.save();
        vg.globalAlpha(0.45f);
        try {
            body.run();
        } finally {
            vg.restore();
        }
    }

    /** Restricts drawing to the given rectangle until {@link #unclip()}. Calls must be balanced. */
    public void clip(float x, float y, float width, float height) {
        vg.save();
        vg.intersectScissor(x, y, width, height);
    }

    public void unclip() {
        vg.restore();
    }

    // ---- raw OpenGL surfaces ----

    private final Map<Object, GlSurface> surfaces = new HashMap<>();

    /** The OpenGL calls for {@link #gl}; the same on desktop and Android. */
    public Gl openGl() {
        return vg.openGl();
    }

    /**
     * Lets {@code painter} draw with raw OpenGL into an offscreen surface (4x antialiased, with a depth buffer),
     * then shows the result at (x, y, width, height) like an image: clipped, scrolled and rounded like everything else.
     * {@code owner} identifies the surface between frames (usually the widget itself).
     */
    public void gl(Object owner, float x, float y, float width, float height, float radius, GlPainter painter) {
        int pixelWidth = Math.max(1, Math.round(width * pixelRatio));
        int pixelHeight = Math.max(1, Math.round(height * pixelRatio));
        GlSurface surface = surfaces.computeIfAbsent(owner, o -> new GlSurface());
        Gl opengl = vg.openGl();
        surface.render(vg, opengl, pixelWidth, pixelHeight, frame, painter);
        opengl.bindFramebuffer(Gl.FRAMEBUFFER, 0); // back to what NanoVG draws into
        opengl.viewport(0, 0, framebufferWidth, framebufferHeight);
        int image = surface.nvgImage;
        vg.imagePattern(x, y, width, height, image, 1);
        vg.beginPath();
        vg.roundedRect(x, y, width, height, radius);
        vg.fill();
    }

    /** Frees surfaces nobody drew for a while (their widget is gone or hidden). Called once per frame. */
    void collectSurfaces() {
        surfaces.values().removeIf(surface -> {
            if (frame - surface.lastFrame > 300) {
                surface.free(vg, vg.openGl());
                return true;
            }
            return false;
        });
    }

    void free() {
        surfaces.values().forEach(surface -> surface.free(vg, vg.openGl()));
        surfaces.clear();
        for (int handle : images.values()) {
            if (handle > 0) {
                vg.deleteImage(handle);
            }
        }
        images.clear();
    }

    private void font(Weight weight, float size, int align) {
        vg.font(weight == Weight.SEMIBOLD ? semiboldFont : regularFont, size, LINE_HEIGHT, align);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80 || Character.isSurrogate((char) codePoint) && codePoint < 0x10000) return 1; // lone surrogate → '?'
        if (codePoint < 0x800) return 2;
        if (codePoint < 0x10000) return 3;
        return 4;
    }

    private static int vgAlign(Align align) {
        return switch (align) {
            case LEFT -> Vg.ALIGN_LEFT;
            case CENTER -> Vg.ALIGN_CENTER;
            case RIGHT -> Vg.ALIGN_RIGHT;
        };
    }

    private void fillColor(Color c) {
        vg.fillColor(c.r(), c.g(), c.b(), c.a());
    }
}
