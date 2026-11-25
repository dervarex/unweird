package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Touch input through the real {@link Window}, drawn with a fake {@link Vg}: taps click, drags scroll. */
class TouchTest {

    static final List<String> CLICKS = new ArrayList<>();

    public static class LongPage extends Screen {
        Button top = new Button("Top").onClick(() -> CLICKS.add("top"));
        Slider volume = new Slider("Volume", 0, 100);
        Text t1 = new Text(filler());
        Text t2 = new Text(filler());
        Text t3 = new Text(filler());
        Text t4 = new Text(filler());
        Text t5 = new Text(filler());
        Button bottom = new Button("Bottom").onClick(() -> CLICKS.add("bottom"));

        static String filler() {
            return "word ".repeat(400);
        }
    }

    private final FakePlatform platform = new FakePlatform();
    private Window window;
    private LongPage page;

    private void open() {
        CLICKS.clear();
        window = new Window(Theme.light(), new App("test"), platform, new FakeVg());
        Unweird.begin(window, LongPage.class);
        page = (LongPage) Unweird.shownScreen();
        frame();
    }

    @AfterEach
    void close() {
        if (window != null) {
            Unweird.end();
        }
    }

    private void frame() {
        window.runPending();
        window.render(400, 800, 1, 1);
    }

    private static float centerX(Widget w) {
        return w.getX() + w.getWidth() / 2;
    }

    private static float centerY(Widget w) {
        return w.getY() + w.getHeight() / 2;
    }

    @Test
    void tapClicks() {
        open();
        window.touchDown(centerX(page.top), centerY(page.top));
        window.touchUp(centerX(page.top), centerY(page.top), false);
        assertEquals(List.of("top"), CLICKS);
    }

    @Test
    void draggingScrollsInsteadOfClicking() {
        open();
        float before = page.top.getY();
        float x = centerX(page.top);
        float y = centerY(page.top);
        window.touchDown(x, y);
        for (int i = 1; i <= 10; i++) {
            platform.now += 0.2; // slow: no fling
            window.touchMoved(x, y - i * 20);
        }
        window.touchUp(x, y - 200, false);
        frame();
        assertTrue(CLICKS.isEmpty(), "a drag isn't a click");
        assertEquals(before - 200, page.top.getY(), 1, "the page moved with the finger");
    }

    @Test
    void smallWobbleStillClicks() {
        open();
        float x = centerX(page.top);
        float y = centerY(page.top);
        window.touchDown(x, y);
        window.touchMoved(x + 3, y + 2);
        window.touchUp(x + 3, y + 2, false);
        assertEquals(List.of("top"), CLICKS);
    }

    @Test
    void slidersTakeTheDrag() {
        open();
        float before = page.top.getY();
        float y = centerY(page.volume);
        window.touchDown(page.volume.getX() + 2, y);
        window.touchMoved(page.volume.getX() + page.volume.getWidth() - 2, y + 30);
        window.touchUp(page.volume.getX() + page.volume.getWidth() - 2, y + 30, false);
        frame();
        assertTrue(page.volume.value() > 90, "dragged to the end, was " + page.volume.value());
        assertEquals(before, page.top.getY(), "the page didn't scroll");
    }

    @Test
    void flingKeepsScrolling() {
        open();
        float x = centerX(page.top);
        float y = 600;
        window.touchDown(x, y);
        for (int i = 1; i <= 5; i++) {
            platform.now += 0.01; // fast: 2000 px/s
            window.touchMoved(x, y - i * 20);
        }
        window.touchUp(x, y - 100, false);
        frame();
        float released = page.top.getY();
        platform.now += 0.016;
        frame();
        assertTrue(window.animating(), "still moving");
        assertTrue(page.top.getY() < released, "kept scrolling after the finger left");
    }

    @Test
    void insetsMoveContentAndInput() {
        open();
        float before = page.top.getY();
        window.insets(0, 40, 0, 0);
        frame();
        assertEquals(before, page.top.getY(), "layout coordinates don't include the inset");
        // in window coordinates the button is now 40 lower
        window.touchDown(centerX(page.top), centerY(page.top) + 40);
        window.touchUp(centerX(page.top), centerY(page.top) + 40, false);
        assertEquals(List.of("top"), CLICKS);
    }

    @Test
    void fingersDontHover() {
        open();
        window.touchDown(centerX(page.top), centerY(page.top));
        window.touchUp(centerX(page.top), centerY(page.top), false);
        assertFalse(page.top.hovered);
    }

    static final class FakePlatform implements Platform {
        double now = 1;

        @Override
        public double time() {
            return now;
        }

        @Override
        public void wake() {
        }

        @Override
        public void close() {
        }

        @Override
        public String clipboard() {
            return null;
        }

        @Override
        public void clipboard(String text) {
        }

        @Override
        public void cursor(Cursor cursor) {
        }

        @Override
        public void title(String title) {
        }

        @Override
        public int modifiers() {
            return 0;
        }
    }

    /** Draws nothing; every character is 7 pixels wide. */
    static final class FakeVg implements Vg {
        private int fonts;

        @Override public void beginFrame(float width, float height, float pixelRatio) { }
        @Override public void endFrame() { }
        @Override public void clear(int w, int h, float r, float g, float b) { }
        @Override public void beginPath() { }
        @Override public void rect(float x, float y, float width, float height) { }
        @Override public void roundedRect(float x, float y, float width, float height, float radius) { }
        @Override public void circle(float centerX, float centerY, float radius) { }
        @Override public void moveTo(float x, float y) { }
        @Override public void lineTo(float x, float y) { }
        @Override public void hole() { }
        @Override public void fillColor(float r, float g, float b, float a) { }
        @Override public void strokeColor(float r, float g, float b, float a) { }
        @Override public void strokeWidth(float width) { }
        @Override public void roundLines() { }
        @Override public void boxGradient(float x, float y, float w, float h, float r, float f, float r1, float g1, float b1, float a1,
                                          float r2, float g2, float b2, float a2) { }
        @Override public void imagePattern(float x, float y, float width, float height, int image, float alpha) { }
        @Override public void fill() { }
        @Override public void stroke() { }
        @Override public void save() { }
        @Override public void restore() { }
        @Override public void globalAlpha(float alpha) { }
        @Override public void translate(float x, float y) { }
        @Override public void intersectScissor(float x, float y, float width, float height) { }
        @Override public void font(int face, float size, float lineHeight, int align) { }
        @Override public float textAdvance(String text) { return text.codePointCount(0, text.length()) * 7; }
        @Override public void text(float x, float y, String text) { }

        @Override
        public int glyphPositions(byte[] utf8, int[] byteOffsets, float[] xs, float[] maxXs) {
            String text = new String(utf8, java.nio.charset.StandardCharsets.UTF_8);
            int glyph = 0;
            for (int i = 0, b = 0; i < text.length() && glyph < xs.length; glyph++) {
                int cp = text.codePointAt(i);
                byteOffsets[glyph] = b;
                xs[glyph] = glyph * 7;
                maxXs[glyph] = glyph * 7 + 7;
                b += new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                i += Character.charCount(cp);
            }
            return glyph;
        }

        @Override public int createFont(String name, byte[] data) { return fonts++; }
        @Override public void addFallbackFont(int base, int fallback) { }
        @Override public int createImage(byte[] encoded) { return 0; }
        @Override public int[] imageSize(int image) { return new int[]{0, 0}; }
        @Override public void deleteImage(int image) { }
        @Override public int imageFromTexture(int texture, int width, int height) { return 0; }
        @Override public Gl openGl() { throw new UnsupportedOperationException(); }
        @Override public void delete() { }
    }
}
