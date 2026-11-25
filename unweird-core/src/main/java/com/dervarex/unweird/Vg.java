package com.dervarex.unweird;

/**
 * The NanoVG calls {@link Canvas} and {@link Window} make, so the same drawing code runs on LWJGL's NanoVG (desktop)
 * and on the NanoVG build that ships with unweird-android. Colors are straight (not premultiplied) RGBA 0..1.
 * Strings are passed as Java strings; implementations encode them as UTF-8.
 */
interface Vg {

    int ALIGN_LEFT = 1;
    int ALIGN_CENTER = 2;
    int ALIGN_RIGHT = 4;
    int ALIGN_TOP = 8;
    int ALIGN_MIDDLE = 16;

    void beginFrame(float width, float height, float pixelRatio);

    void endFrame();

    /** Clears the whole target framebuffer with an opaque color; called before {@link #beginFrame}. */
    void clear(int framebufferWidth, int framebufferHeight, float r, float g, float b);

    void beginPath();

    void rect(float x, float y, float width, float height);

    void roundedRect(float x, float y, float width, float height, float radius);

    void circle(float centerX, float centerY, float radius);

    void moveTo(float x, float y);

    void lineTo(float x, float y);

    /** The last added sub-path is a hole (for shadows around a box). */
    void hole();

    void fillColor(float r, float g, float b, float a);

    void strokeColor(float r, float g, float b, float a);

    void strokeWidth(float width);

    /** Round line caps and joins. */
    void roundLines();

    /** The fill becomes a box gradient (a blurred box from {@code inner} to {@code outer}). */
    void boxGradient(float x, float y, float width, float height, float radius, float feather,
                     float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2);

    /** The fill becomes {@code image}, stretched over the given rectangle. */
    void imagePattern(float x, float y, float width, float height, int image, float alpha);

    void fill();

    void stroke();

    void save();

    void restore();

    void globalAlpha(float alpha);

    void translate(float x, float y);

    void intersectScissor(float x, float y, float width, float height);

    void font(int face, float size, float lineHeight, int align);

    /** Horizontal advance of one line. */
    float textAdvance(String text);

    void text(float x, float y, String text);

    /**
     * Glyph positions of one line drawn at x = 0: per glyph, the UTF-8 byte offset where it starts, its x and
     * its right edge. Returns the number of glyphs written.
     */
    int glyphPositions(byte[] utf8, int[] byteOffsets, float[] xs, float[] maxXs);

    /** Registers a TrueType font; returns its id or a negative number if the data isn't a font. */
    int createFont(String name, byte[] data);

    void addFallbackFont(int base, int fallback);

    /** Decodes an encoded image (PNG, JPEG, ...). Returns a handle &gt; 0, or 0 if it can't be decoded. */
    int createImage(byte[] encoded);

    int[] imageSize(int image);

    void deleteImage(int image);

    /**
     * Paints raw OpenGL into an offscreen surface owned by {@code owner} (see {@link Canvas#gl}) and returns the
     * image showing it. Afterwards the target framebuffer is bound again.
     */
    int gl(Object owner, int pixelWidth, int pixelHeight, long frame, int framebufferWidth, int framebufferHeight, GlPainter painter);

    /** Frees GL surfaces not painted since {@code oldestFrame}. */
    void collectSurfaces(long oldestFrame);

    /** Frees everything, including the context. */
    void delete();
}
