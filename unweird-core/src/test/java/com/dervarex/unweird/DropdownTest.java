package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

class DropdownTest {

    private final FieldTest.FakeHost host = new FieldTest.FakeHost();

    @Test
    void keyboardOpensMovesAndChooses() {
        List<String> changes = new ArrayList<>();
        Dropdown<String> language = new Dropdown<>("Language", List.of("English", "Deutsch", "Français"))
                .onChange(changes::add);

        assertTrue(language.keyPressed(GLFW_KEY_DOWN, 0, host));
        assertTrue(language.isOpen());
        language.keyPressed(GLFW_KEY_DOWN, 0, host);
        language.keyPressed(GLFW_KEY_DOWN, 0, host);
        language.keyPressed(GLFW_KEY_DOWN, 0, host); // clamps at the last option
        language.keyPressed(GLFW_KEY_ENTER, 0, host);

        assertFalse(language.isOpen());
        assertNull(host.overlay);
        assertEquals("Français", language.selected());
        assertEquals(List.of("Français"), changes);
    }

    @Test
    void reopeningHighlightsTheCurrentChoiceAndSameChoiceDoesntFire() {
        List<String> changes = new ArrayList<>();
        Dropdown<String> size = new Dropdown<>("Size", List.of("S", "M", "L")).select("M").onChange(changes::add);
        size.activate(host);
        size.keyPressed(GLFW_KEY_ENTER, 0, host);
        assertEquals("M", size.selected());
        assertTrue(changes.isEmpty());
    }

    @Test
    void clickingTogglesTheList() {
        Dropdown<String> d = new Dropdown<>("Pick", List.of("a", "b"));
        d.activate(host);
        assertTrue(d.isOpen());
        d.activate(host);
        assertFalse(d.isOpen());
    }

    @Test
    void closingFromOutsideResetsState() {
        Dropdown<String> d = new Dropdown<>("Pick", List.of("a", "b"));
        d.activate(host);
        host.closeOverlay(); // what Escape or a click outside does
        assertFalse(d.isOpen());
    }

    @Test
    void longListsScrollToKeepTheHighlightVisible() {
        List<Integer> numbers = IntStream.range(0, 20).boxed().toList();
        Dropdown<Integer> d = new Dropdown<>("Number", numbers);
        d.activate(host);
        @SuppressWarnings("unchecked")
        var popup = (Dropdown<Integer>.Popup) host.overlay;
        d.keyPressed(GLFW_KEY_END, 0, host);
        assertEquals(19, popup.highlighted);
        assertEquals(12, popup.first);
        d.keyPressed(GLFW_KEY_HOME, 0, host);
        assertEquals(0, popup.first);
    }

    @Test
    void labelsAndValidation() {
        record Lang(String code, String name) {
        }
        Lang en = new Lang("en", "English");
        Dropdown<Lang> d = new Dropdown<>("Language", List.of(en)).labels(Lang::name);
        assertEquals("English", d.labelOf(en));
        assertThrows(IllegalArgumentException.class, () -> d.select(new Lang("xx", "Nope")));
        d.select(en);
        d.options(List.of(new Lang("de", "Deutsch")));
        assertNull(d.selected());
    }

    @Test
    void emptyDropdownsDontOpen() {
        Dropdown<String> d = new Dropdown<>("Nothing", List.of());
        d.activate(host);
        assertFalse(d.isOpen());
    }
}
