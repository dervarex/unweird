package com.dervarex.unweird;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGGlyphPosition;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;
import static org.lwjgl.system.MemoryUtil.NULL;

/** {@link Vg} on LWJGL's NanoVG (OpenGL 3.3). Desktop. */
final class LwjglVg implements Vg {

    private final long vg;
    private final NVGColor color = NVGColor.malloc();
    private final NVGColor color2 = NVGColor.malloc();
    private final NVGPaint paint = NVGPaint.malloc();
    private final float[] bounds = new float[4];
    /** Font data must outlive the context: NanoVG reads from it while drawing. */
    private final List<ByteBuffer> fonts = new ArrayList<>();
    private final Map<Object, GlSurface> surfaces = new HashMap<>();

    LwjglVg() {
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == NULL) {
            throw new IllegalStateException("unweird: could not initialise NanoVG");
        }
    }

    @Override
    public void beginFrame(float width, float height, float pixelRatio) {
        nvgBeginFrame(vg, width, height, pixelRatio);
    }

    @Override
    public void endFrame() {
        nvgEndFrame(vg);
    }

    @Override
    public void clear(int framebufferWidth, int framebufferHeight, float r, float g, float b) {
        org.lwjgl.opengl.GL11C.glViewport(0, 0, framebufferWidth, framebufferHeight);
        org.lwjgl.opengl.GL11C.glClearColor(r, g, b, 1);
        org.lwjgl.opengl.GL11C.glClear(org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11C.GL_STENCIL_BUFFER_BIT);
    }

    @Override
    public void beginPath() {
        nvgBeginPath(vg);
    }

    @Override
    public void rect(float x, float y, float width, float height) {
        nvgRect(vg, x, y, width, height);
    }

    @Override
    public void roundedRect(float x, float y, float width, float height, float radius) {
        nvgRoundedRect(vg, x, y, width, height, radius);
    }

    @Override
    public void circle(float centerX, float centerY, float radius) {
        nvgCircle(vg, centerX, centerY, radius);
    }

    @Override
    public void moveTo(float x, float y) {
        nvgMoveTo(vg, x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        nvgLineTo(vg, x, y);
    }

    @Override
    public void hole() {
        nvgPathWinding(vg, NVG_HOLE);
    }

    @Override
    public void fillColor(float r, float g, float b, float a) {
        nvgFillColor(vg, nvgRGBAf(r, g, b, a, color));
    }

    @Override
    public void strokeColor(float r, float g, float b, float a) {
        nvgStrokeColor(vg, nvgRGBAf(r, g, b, a, color));
    }

    @Override
    public void strokeWidth(float width) {
        nvgStrokeWidth(vg, width);
    }

    @Override
    public void roundLines() {
        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
    }

    @Override
    public void boxGradient(float x, float y, float width, float height, float radius, float feather,
                            float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        nvgBoxGradient(vg, x, y, width, height, radius, feather, nvgRGBAf(r1, g1, b1, a1, color), nvgRGBAf(r2, g2, b2, a2, color2), paint);
        nvgFillPaint(vg, paint);
    }

    @Override
    public void imagePattern(float x, float y, float width, float height, int image, float alpha) {
        nvgImagePattern(vg, x, y, width, height, 0, image, alpha, paint);
        nvgFillPaint(vg, paint);
    }

    @Override
    public void fill() {
        nvgFill(vg);
    }

    @Override
    public void stroke() {
        nvgStroke(vg);
    }

    @Override
    public void save() {
        nvgSave(vg);
    }

    @Override
    public void restore() {
        nvgRestore(vg);
    }

    @Override
    public void globalAlpha(float alpha) {
        nvgGlobalAlpha(vg, alpha);
    }

    @Override
    public void translate(float x, float y) {
        nvgTranslate(vg, x, y);
    }

    @Override
    public void intersectScissor(float x, float y, float width, float height) {
        nvgIntersectScissor(vg, x, y, width, height);
    }

    @Override
    public void font(int face, float size, float lineHeight, int align) {
        nvgFontFaceId(vg, face);
        nvgFontSize(vg, size);
        nvgTextLineHeight(vg, lineHeight);
        nvgTextAlign(vg, align);
    }

    @Override
    public float textAdvance(String text) {
        return nvgTextBounds(vg, 0, 0, text, bounds);
    }

    @Override
    public void text(float x, float y, String text) {
        nvgText(vg, x, y, text);
    }

    @Override
    public int glyphPositions(byte[] utf8, int[] byteOffsets, float[] xs, float[] maxXs) {
        ByteBuffer buffer = MemoryUtil.memAlloc(utf8.length);
        NVGGlyphPosition.Buffer glyphs = NVGGlyphPosition.malloc(xs.length);
        try {
            buffer.put(utf8).flip();
            long start = MemoryUtil.memAddress(buffer);
            int count = nvgTextGlyphPositions(vg, 0, 0, buffer, glyphs);
            for (int g = 0; g < count; g++) {
                NVGGlyphPosition glyph = glyphs.get(g);
                byteOffsets[g] = (int) (glyph.str() - start);
                xs[g] = glyph.x();
                maxXs[g] = glyph.maxx();
            }
            return count;
        } finally {
            glyphs.free();
            MemoryUtil.memFree(buffer);
        }
    }

    @Override
    public int createFont(String name, byte[] data) {
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length).put(data).flip();
        int font = nvgCreateFontMem(vg, name, buffer, false);
        if (font < 0) {
            MemoryUtil.memFree(buffer);
        } else {
            fonts.add(buffer);
        }
        return font;
    }

    @Override
    public void addFallbackFont(int base, int fallback) {
        nvgAddFallbackFontId(vg, base, fallback);
    }

    @Override
    public int createImage(byte[] encoded) {
        ByteBuffer buffer = MemoryUtil.memAlloc(encoded.length);
        int handle;
        try {
            buffer.put(encoded).flip();
            handle = nvgCreateImageMem(vg, 0, buffer);
        } finally {
            MemoryUtil.memFree(buffer); // NanoVG has uploaded the decoded pixels by now
        }
        return handle > 0 ? handle : decodeWithImageIo(encoded);
    }

    /** Formats NanoVG can't read (WebP, ...) via javax.imageio and whatever plugins are on the classpath. */
    private int decodeWithImageIo(byte[] data) {
        java.awt.image.BufferedImage image;
        try {
            image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
        } catch (java.io.IOException | RuntimeException e) {
            return 0;
        }
        if (image == null) {
            return 0;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
        try {
            for (int pixel : argb) {
                rgba.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) (pixel >>> 24));
            }
            rgba.flip();
            return Math.max(0, nvgCreateImageRGBA(vg, width, height, 0, rgba));
        } finally {
            MemoryUtil.memFree(rgba);
        }
    }

    @Override
    public int[] imageSize(int image) {
        int[] w = new int[1];
        int[] h = new int[1];
        nvgImageSize(vg, image, w, h);
        return new int[]{w[0], h[0]};
    }

    @Override
    public void deleteImage(int image) {
        nvgDeleteImage(vg, image);
    }

    @Override
    public int gl(Object owner, int pixelWidth, int pixelHeight, long frame, int framebufferWidth, int framebufferHeight, GlPainter painter) {
        GlSurface surface = surfaces.computeIfAbsent(owner, o -> new GlSurface());
        surface.render(vg, pixelWidth, pixelHeight, frame, painter);
        // back to what NanoVG draws into
        org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0);
        org.lwjgl.opengl.GL11C.glViewport(0, 0, framebufferWidth, framebufferHeight);
        return surface.nvgImage;
    }

    @Override
    public void collectSurfaces(long oldestFrame) {
        surfaces.values().removeIf(surface -> {
            if (surface.lastFrame < oldestFrame) {
                surface.free(vg);
                return true;
            }
            return false;
        });
    }

    @Override
    public void delete() {
        surfaces.values().forEach(surface -> surface.free(vg));
        surfaces.clear();
        nvgDelete(vg);
        fonts.forEach(MemoryUtil::memFree);
        fonts.clear();
        color.free();
        color2.free();
        paint.free();
    }
}
