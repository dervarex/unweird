package com.dervarex.unweird;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.nio.charset.StandardCharsets;

/** {@link Vg} on the NanoVG build in this library (OpenGL ES 3, through JNI). Lives on the GL thread. */
final class AndroidVg implements Vg {

    static {
        System.loadLibrary("unweird");
    }

    private final long ctx;
    private boolean warnedGl;

    AndroidVg() {
        ctx = nCreate();
        if (ctx == 0) {
            throw new IllegalStateException("unweird: could not initialise NanoVG (OpenGL ES 3 is required)");
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void beginFrame(float width, float height, float pixelRatio) {
        nBeginFrame(ctx, width, height, pixelRatio);
    }

    @Override
    public void endFrame() {
        nEndFrame(ctx);
    }

    @Override
    public void clear(int framebufferWidth, int framebufferHeight, float r, float g, float b) {
        nClear(framebufferWidth, framebufferHeight, r, g, b);
    }

    @Override
    public void beginPath() {
        nBeginPath(ctx);
    }

    @Override
    public void rect(float x, float y, float width, float height) {
        nRect(ctx, x, y, width, height);
    }

    @Override
    public void roundedRect(float x, float y, float width, float height, float radius) {
        nRoundedRect(ctx, x, y, width, height, radius);
    }

    @Override
    public void circle(float centerX, float centerY, float radius) {
        nCircle(ctx, centerX, centerY, radius);
    }

    @Override
    public void moveTo(float x, float y) {
        nMoveTo(ctx, x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        nLineTo(ctx, x, y);
    }

    @Override
    public void hole() {
        nHole(ctx);
    }

    @Override
    public void fillColor(float r, float g, float b, float a) {
        nFillColor(ctx, r, g, b, a);
    }

    @Override
    public void strokeColor(float r, float g, float b, float a) {
        nStrokeColor(ctx, r, g, b, a);
    }

    @Override
    public void strokeWidth(float width) {
        nStrokeWidth(ctx, width);
    }

    @Override
    public void roundLines() {
        nRoundLines(ctx);
    }

    @Override
    public void boxGradient(float x, float y, float width, float height, float radius, float feather,
                            float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        nBoxGradient(ctx, x, y, width, height, radius, feather, r1, g1, b1, a1, r2, g2, b2, a2);
    }

    @Override
    public void imagePattern(float x, float y, float width, float height, int image, float alpha) {
        nImagePattern(ctx, x, y, width, height, image, alpha);
    }

    @Override
    public void fill() {
        nFill(ctx);
    }

    @Override
    public void stroke() {
        nStroke(ctx);
    }

    @Override
    public void save() {
        nSave(ctx);
    }

    @Override
    public void restore() {
        nRestore(ctx);
    }

    @Override
    public void globalAlpha(float alpha) {
        nGlobalAlpha(ctx, alpha);
    }

    @Override
    public void translate(float x, float y) {
        nTranslate(ctx, x, y);
    }

    @Override
    public void intersectScissor(float x, float y, float width, float height) {
        nIntersectScissor(ctx, x, y, width, height);
    }

    @Override
    public void font(int face, float size, float lineHeight, int align) {
        nFont(ctx, face, size, lineHeight, align);
    }

    @Override
    public float textAdvance(String text) {
        return nTextAdvance(ctx, utf8(text));
    }

    @Override
    public void text(float x, float y, String text) {
        nText(ctx, x, y, utf8(text));
    }

    @Override
    public int glyphPositions(byte[] utf8, int[] byteOffsets, float[] xs, float[] maxXs) {
        return nGlyphPositions(ctx, utf8, byteOffsets, xs, maxXs);
    }

    @Override
    public int createFont(String name, byte[] data) {
        return nCreateFont(ctx, name, data);
    }

    @Override
    public void addFallbackFont(int base, int fallback) {
        nAddFallbackFont(ctx, base, fallback);
    }

    @Override
    public int createImage(byte[] encoded) {
        int handle = nCreateImage(ctx, encoded);
        return handle > 0 ? handle : decodeWithBitmapFactory(encoded);
    }

    /** Formats NanoVG can't read (WebP, HEIF, ...): Android's own decoders. */
    private int decodeWithBitmapFactory(byte[] encoded) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inPremultiplied = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
        if (bitmap == null) {
            return 0;
        }
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] argb = new int[width * height];
            bitmap.getPixels(argb, 0, width, 0, 0, width, height);
            byte[] rgba = new byte[argb.length * 4];
            for (int i = 0; i < argb.length; i++) {
                int pixel = argb[i];
                rgba[i * 4] = (byte) (pixel >> 16);
                rgba[i * 4 + 1] = (byte) (pixel >> 8);
                rgba[i * 4 + 2] = (byte) pixel;
                rgba[i * 4 + 3] = (byte) (pixel >>> 24);
            }
            return Math.max(0, nCreateImageRgba(ctx, width, height, rgba));
        } finally {
            bitmap.recycle();
        }
    }

    @Override
    public int[] imageSize(int image) {
        return nImageSize(ctx, image);
    }

    @Override
    public void deleteImage(int image) {
        nDeleteImage(ctx, image);
    }

    @Override
    public int gl(Object owner, int pixelWidth, int pixelHeight, long frame, int framebufferWidth, int framebufferHeight, GlPainter painter) {
        if (!warnedGl) {
            warnedGl = true;
            System.err.println("unweird: Canvas.gl (3D views) isn't available on Android yet; " + owner.getClass().getSimpleName() + " stays empty");
        }
        return 0;
    }

    @Override
    public void collectSurfaces(long oldestFrame) {
    }

    @Override
    public void delete() {
        nDelete(ctx);
    }

    private static native long nCreate();

    private static native void nDelete(long ctx);

    private static native void nClear(int width, int height, float r, float g, float b);

    private static native void nBeginFrame(long ctx, float width, float height, float ratio);

    private static native void nEndFrame(long ctx);

    private static native void nBeginPath(long ctx);

    private static native void nRect(long ctx, float x, float y, float w, float h);

    private static native void nRoundedRect(long ctx, float x, float y, float w, float h, float r);

    private static native void nCircle(long ctx, float x, float y, float r);

    private static native void nMoveTo(long ctx, float x, float y);

    private static native void nLineTo(long ctx, float x, float y);

    private static native void nHole(long ctx);

    private static native void nFillColor(long ctx, float r, float g, float b, float a);

    private static native void nStrokeColor(long ctx, float r, float g, float b, float a);

    private static native void nStrokeWidth(long ctx, float width);

    private static native void nRoundLines(long ctx);

    private static native void nBoxGradient(long ctx, float x, float y, float w, float h, float r, float f,
                                            float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2);

    private static native void nImagePattern(long ctx, float x, float y, float w, float h, int image, float alpha);

    private static native void nFill(long ctx);

    private static native void nStroke(long ctx);

    private static native void nSave(long ctx);

    private static native void nRestore(long ctx);

    private static native void nGlobalAlpha(long ctx, float alpha);

    private static native void nTranslate(long ctx, float x, float y);

    private static native void nIntersectScissor(long ctx, float x, float y, float w, float h);

    private static native void nFont(long ctx, int face, float size, float lineHeight, int align);

    private static native float nTextAdvance(long ctx, byte[] utf8);

    private static native void nText(long ctx, float x, float y, byte[] utf8);

    private static native int nGlyphPositions(long ctx, byte[] utf8, int[] offsets, float[] xs, float[] maxXs);

    private static native int nCreateFont(long ctx, String name, byte[] data);

    private static native void nAddFallbackFont(long ctx, int base, int fallback);

    private static native int nCreateImage(long ctx, byte[] encoded);

    private static native int nCreateImageRgba(long ctx, int width, int height, byte[] rgba);

    private static native int[] nImageSize(long ctx, int image);

    private static native void nDeleteImage(long ctx, int image);
}
