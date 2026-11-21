package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

class InputApiTest {

    private static Pointer at(float x, Pointer.Button button) {
        return new Pointer(x, 0, button, 1, false, false, false, 0, 0);
    }

    @Test
    void keyPatternsMatchNamesAndModifiers() {
        KeyPress ctrlS = new KeyPress(GLFW_KEY_S, GLFW_MOD_CONTROL, false);
        assertTrue(ctrlS.is("Ctrl+S"));
        assertTrue(ctrlS.is("ctrl + s"));
        assertFalse(ctrlS.is("S"));
        assertFalse(ctrlS.is("Ctrl+Shift+S"));
        assertTrue(new KeyPress(GLFW_KEY_Z, GLFW_MOD_CONTROL | GLFW_MOD_SHIFT, false).is("Ctrl+Shift+Z"));
        assertTrue(new KeyPress(GLFW_KEY_ESCAPE, 0, false).is("Esc"));
        assertTrue(new KeyPress(GLFW_KEY_F5, 0, false).is("F5"));
        assertTrue(new KeyPress(GLFW_KEY_PAGE_UP, 0, false).is("pageup"));
        assertTrue(new KeyPress(GLFW_KEY_EQUAL, GLFW_MOD_CONTROL, false).is("Ctrl++"));
        assertThrows(IllegalArgumentException.class, () -> new KeyPress(GLFW_KEY_A, 0, false).is("Ctrl+"));
    }

    @Test
    void customWidgetsGetEventsThroughTheDefaultPaths() {
        List<String> seen = new ArrayList<>();
        Widget custom = new Widget() {
            @Override protected Size measure(Canvas canvas, float maxWidth) { return new Size(10, 10); }
            @Override protected void draw(Canvas canvas) { }
            @Override protected boolean focusable() { return true; }
            @Override protected void onPress(Pointer p) { seen.add("press " + p.button()); }
            @Override protected void onDrag(Pointer p) { seen.add("drag " + p.dx()); }
            @Override protected void onRelease(Pointer p) { seen.add("release"); }
            @Override protected boolean onKey(KeyPress k) { seen.add("key " + k.name()); return k.is("Space"); }
            @Override protected void onType(String text) { seen.add("type " + text); }
            @Override protected boolean onScroll(Pointer p, double dx, double dy) { seen.add("scroll " + dy); return true; }
        };
        FieldTest.FakeHost host = new FieldTest.FakeHost();
        custom.pointerDown(at(1, Pointer.Button.RIGHT));
        custom.pointerDrag(new Pointer(2, 0, Pointer.Button.RIGHT, 0, false, false, false, 3, 0));
        custom.pointerUp(at(2, Pointer.Button.RIGHT));
        assertTrue(custom.keyPressed(GLFW_KEY_SPACE, 0, host));
        assertFalse(custom.keyPressed(GLFW_KEY_X, 0, host));
        custom.typed('ä', host);
        assertTrue(custom.scrolled(at(0, Pointer.Button.NONE), 0, -1));
        assertEquals(List.of("press RIGHT", "drag 3.0", "release", "key Space", "key X", "type ä", "scroll -1.0"), seen);
    }

    @Test
    void sliderDragsStepsAndClamps() {
        List<Double> changes = new ArrayList<>();
        Slider slider = new Slider("Volume", 0, 100).step(10).onChange(changes::add);
        slider.place(null, 0, 0, 218, 46); // track from 9 to 209: 2px per unit
        slider.pointerDown(at(9 + 100, Pointer.Button.LEFT));
        assertEquals(50, slider.value());
        slider.pointerDrag(at(500, Pointer.Button.LEFT));
        assertEquals(100, slider.value());
        FieldTest.FakeHost host = new FieldTest.FakeHost();
        slider.keyPressed(GLFW_KEY_LEFT, 0, host);
        slider.keyPressed(GLFW_KEY_HOME, 0, host);
        slider.keyPressed(GLFW_KEY_HOME, 0, host); // no change, no event
        assertEquals(List.of(50.0, 100.0, 90.0, 0.0), changes);
        assertEquals(33.0, new Slider("x", 0, 100).step(0).value(33).value());
        assertEquals(30.0, new Slider("x", 0, 100).step(10).value(27).value());
    }

    @Test
    void sliderOnlyTakesTheWheelWhenFocused() {
        Slider slider = new Slider("x", 0, 10);
        assertFalse(slider.scrolled(at(0, Pointer.Button.NONE), 0, 1));
        slider.focused = true;
        assertTrue(slider.scrolled(at(0, Pointer.Button.NONE), 0, 1));
        assertEquals(1, slider.value());
    }
}
