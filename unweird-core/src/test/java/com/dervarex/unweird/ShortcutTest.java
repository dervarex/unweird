package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Shortcut;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

class ShortcutTest {

    static final List<String> LOG = new ArrayList<>();

    static class Editor extends Component {
        Field text = new Field("Text");

        @Shortcut("Ctrl+S")
        void save() {
            LOG.add("editor save");
        }
    }

    static class Workspace extends Screen {
        Field search = new Field("Search");
        Editor editor = new Editor();

        {
            shortcut("Ctrl+F", () -> LOG.add("screen find"));
        }

        @Shortcut({"Ctrl+S", "F2"})
        void saveAll() {
            LOG.add("screen save");
        }
    }

    static class Broken extends Screen {
        @Shortcut("Ctrl+S")
        void save(int how) {
        }
    }

    private final FieldTest.FakeHost host = new FieldTest.FakeHost();

    @AfterEach
    void clear() {
        LOG.clear();
        Shortcuts.GLOBAL.clear();
    }

    private boolean press(int key, int mods, Widget focused, Screen screen) {
        return Shortcuts.dispatch(new KeyPress(key, mods, false), focused, screen, host);
    }

    @Test
    void theNearestComponentWins() {
        Workspace screen = new Workspace();
        screen.focusables(); // resolves the whole tree, like rendering does
        assertTrue(press(GLFW_KEY_S, GLFW_MOD_CONTROL, screen.editor.text, screen));
        assertTrue(press(GLFW_KEY_S, GLFW_MOD_CONTROL, screen.search, screen));
        assertEquals(List.of("editor save", "screen save"), LOG);
    }

    @Test
    void severalPatternsAndMethodShortcuts() {
        Workspace screen = new Workspace();
        screen.focusables(); // resolves the whole tree, like rendering does
        assertTrue(press(GLFW_KEY_F2, 0, null, screen));
        assertTrue(press(GLFW_KEY_F, GLFW_MOD_CONTROL, null, screen));
        assertFalse(press(GLFW_KEY_F, 0, null, screen));
        assertEquals(List.of("screen save", "screen find"), LOG);
    }

    @Test
    void appWideShortcutsComeLast() {
        Unweird.shortcut("Ctrl+S", () -> LOG.add("global save"));
        Unweird.shortcut("F11", () -> LOG.add("fullscreen"));
        Workspace screen = new Workspace();
        screen.focusables(); // resolves the whole tree, like rendering does
        press(GLFW_KEY_S, GLFW_MOD_CONTROL, null, screen);
        press(GLFW_KEY_F11, 0, null, screen);
        assertEquals(List.of("screen save", "fullscreen"), LOG);
    }

    @Test
    void badShortcutMethodsAreReported() {
        var error = assertThrows(IllegalStateException.class, () -> Shortcuts.annotated(Broken.class));
        assertTrue(error.getMessage().contains("without parameters"));
    }
}
