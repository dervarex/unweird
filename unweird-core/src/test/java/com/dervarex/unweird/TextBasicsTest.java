package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.glfw.GLFW.*;

/** Undo/redo, tooltips and maxLines settings (wrapping itself needs a renderer and is checked visually). */
class TextBasicsTest {

    private final FieldTest.FakeHost host = new FieldTest.FakeHost();

    private void type(Field field, String text) {
        text.codePoints().forEach(cp -> field.typed(cp, host));
    }

    @Test
    void undoTakesBackWholeWordsAndRedoBringsThemBack() {
        Field field = new Field("x");
        type(field, "hello world");
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL, host);
        assertEquals("hello ", field.value());
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL, host);
        assertEquals("", field.value());
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL | GLFW_MOD_SHIFT, host);
        assertEquals("hello ", field.value());
        field.keyPressed(GLFW_KEY_Y, GLFW_MOD_CONTROL, host);
        assertEquals("hello world", field.value());
    }

    @Test
    void deletingAndPastingAreSeparateSteps() {
        Field field = new Field("x");
        type(field, "abc");
        field.keyPressed(GLFW_KEY_BACKSPACE, 0, host);
        field.keyPressed(GLFW_KEY_BACKSPACE, 0, host);
        host.clipboard = "XYZ";
        field.keyPressed(GLFW_KEY_V, GLFW_MOD_CONTROL, host);
        assertEquals("aXYZ", field.value());
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL, host);
        assertEquals("a", field.value());
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL, host);
        assertEquals("ab", field.value());
    }

    @Test
    void anEditAfterUndoDropsTheRedoHistory() {
        Field field = new Field("x");
        type(field, "one");
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL, host);
        type(field, "two");
        field.keyPressed(GLFW_KEY_Y, GLFW_MOD_CONTROL, host);
        assertEquals("two", field.value());
    }

    @Test
    void undoTellsChangeHandlers() {
        List<String> changes = new ArrayList<>();
        Field field = new Field("x").onChange(changes::add);
        type(field, "ab");
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL, host);
        assertEquals(List.of("a", "ab", ""), changes);
    }

    @Test
    void settingTheValueClearsHistory() {
        Field field = new Field("x");
        type(field, "typed");
        field.value("set");
        field.keyPressed(GLFW_KEY_Z, GLFW_MOD_CONTROL, host);
        assertEquals("set", field.value());
    }

    @Test
    void tooltipsMakeNonInteractiveWidgetsHoverable() {
        Text text = new Text("Hover me").tooltip("Hi!");
        text.place(null, 0, 0, 100, 20);
        assertSame(text, text.hitTest(10, 10));
        assertNull(new Text("No tip").hitTest(10, 10));
        assertNull(text.tooltip("  ").tooltipText());
    }

    @Test
    void maxLinesMustNotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Text("x").maxLines(-1));
    }
}
