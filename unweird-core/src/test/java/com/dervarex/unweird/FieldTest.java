package com.dervarex.unweird;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

/** Editing behavior of {@link Field}, driven through the same hooks the window uses. Shortcuts use Ctrl, so not on macOS. */
@DisabledOnOs(OS.MAC)
class FieldTest {

    static class FakeHost implements Host {
        String clipboard = "";
        final List<RuntimeException> errors = new ArrayList<>();

        @Override
        public String clipboard() {
            return clipboard;
        }

        @Override
        public void clipboard(String text) {
            clipboard = text;
        }

        @Override
        public void safely(Widget source, Runnable handler) {
            try {
                handler.run();
            } catch (RuntimeException e) {
                errors.add(e);
            }
        }

        Overlay overlay;

        @Override
        public void openOverlay(Overlay next) {
            closeOverlay();
            overlay = next;
        }

        @Override
        public void closeOverlay(Overlay closing) {
            if (overlay == closing) {
                overlay = null;
                closing.closed();
            }
        }

        void closeOverlay() {
            if (overlay != null) {
                closeOverlay(overlay);
            }
        }
    }

    private final FakeHost host = new FakeHost();

    private void type(Field field, String text) {
        text.codePoints().forEach(cp -> field.typed(cp, host));
    }

    private void key(Field field, int key) {
        field.keyPressed(key, 0, host);
    }

    private void key(Field field, int key, int mods) {
        field.keyPressed(key, mods, host);
    }

    @Test
    void typingInsertsAtTheCaret() {
        Field field = new Field("Name");
        type(field, "helo");
        key(field, GLFW_KEY_LEFT);
        type(field, "l");
        assertEquals("hello", field.value());
        assertEquals(4, field.caret());
    }

    @Test
    void controlCharactersAreIgnored() {
        Field field = new Field("Name");
        field.typed('\t', host);
        field.typed('\n', host);
        assertEquals("", field.value());
    }

    @Test
    void backspaceRemovesAWholeEmoji() {
        Field field = new Field("Name");
        type(field, "hi😀");
        key(field, GLFW_KEY_BACKSPACE);
        assertEquals("hi", field.value());
    }

    @Test
    void arrowsNeverLandInsideAnEmoji() {
        Field field = new Field("Name");
        type(field, "a😀b");
        key(field, GLFW_KEY_LEFT);
        key(field, GLFW_KEY_LEFT);
        assertEquals(1, field.caret());
        key(field, GLFW_KEY_RIGHT);
        assertEquals(3, field.caret());
    }

    @Test
    void shiftArrowsSelectAndTypingReplacesTheSelection() {
        Field field = new Field("Name");
        type(field, "hello world");
        key(field, GLFW_KEY_LEFT, GLFW_MOD_SHIFT);
        key(field, GLFW_KEY_LEFT, GLFW_MOD_SHIFT);
        assertEquals("ld", field.selectedText());
        type(field, "k");
        assertEquals("hello work", field.value());
    }

    @Test
    void leftWithASelectionCollapsesToItsStart() {
        Field field = new Field("Name");
        type(field, "abc");
        key(field, GLFW_KEY_HOME, GLFW_MOD_SHIFT);
        key(field, GLFW_KEY_RIGHT);
        assertEquals(3, field.caret());
        assertEquals("", field.selectedText());
    }

    @Test
    void ctrlMovesAndDeletesByWord() {
        Field field = new Field("Name");
        type(field, "one two  three");
        key(field, GLFW_KEY_LEFT, GLFW_MOD_CONTROL);
        assertEquals(9, field.caret());
        key(field, GLFW_KEY_LEFT, GLFW_MOD_CONTROL);
        assertEquals(4, field.caret());
        key(field, GLFW_KEY_END);
        key(field, GLFW_KEY_BACKSPACE, GLFW_MOD_CONTROL);
        assertEquals("one two  ", field.value());
        key(field, GLFW_KEY_HOME);
        key(field, GLFW_KEY_DELETE, GLFW_MOD_CONTROL);
        assertEquals(" two  ", field.value());
    }

    @Test
    void copyCutAndPaste() {
        Field field = new Field("Name");
        type(field, "hello");
        key(field, GLFW_KEY_A, GLFW_MOD_CONTROL);
        key(field, GLFW_KEY_C, GLFW_MOD_CONTROL);
        assertEquals("hello", host.clipboard);

        key(field, GLFW_KEY_X, GLFW_MOD_CONTROL);
        assertEquals("", field.value());

        host.clipboard = "two\nlines\u0007";
        key(field, GLFW_KEY_V, GLFW_MOD_CONTROL);
        assertEquals("two lines", field.value());
    }

    @Test
    void maskedFieldsNeverCopyAndJumpWholeWords() {
        Field field = new Field("Password").masked();
        type(field, "se cret");
        key(field, GLFW_KEY_A, GLFW_MOD_CONTROL);
        key(field, GLFW_KEY_C, GLFW_MOD_CONTROL);
        key(field, GLFW_KEY_X, GLFW_MOD_CONTROL);
        assertEquals("", host.clipboard);
        assertEquals("se cret", field.value());

        key(field, GLFW_KEY_END);
        key(field, GLFW_KEY_LEFT, GLFW_MOD_CONTROL);
        assertEquals(0, field.caret());
    }

    @Test
    void changeAndSubmitHandlersRun() {
        List<String> changes = new ArrayList<>();
        int[] submits = {0};
        Field field = new Field("Name")
                .onChange(changes::add)
                .onSubmit(() -> submits[0]++);
        type(field, "ab");
        key(field, GLFW_KEY_BACKSPACE);
        key(field, GLFW_KEY_ENTER);
        assertEquals(List.of("a", "ab", "a"), changes);
        assertEquals(1, submits[0]);
    }

    @Test
    void aThrowingHandlerIsReportedNotPropagated() {
        Field field = new Field("Name").onSubmit(() -> {
            throw new IllegalStateException("boom");
        });
        key(field, GLFW_KEY_ENTER);
        assertEquals(1, host.errors.size());
    }

    @Test
    void doubleClickLogicSelectsAWord() {
        Field field = new Field("Name").value("hello world");
        // positions are unknown before the first draw, so the caret stays put; place it explicitly
        key(field, GLFW_KEY_HOME);
        key(field, GLFW_KEY_RIGHT);
        field.pointerDown(new Pointer(0, 0, Pointer.Button.LEFT, 2, false, false, false, 0, 0));
        assertEquals("hello", field.selectedText());
        field.pointerDown(new Pointer(0, 0, Pointer.Button.LEFT, 3, false, false, false, 0, 0));
        assertEquals("hello world", field.selectedText());
    }

    @Test
    void buttonsActivateWithEnterAndSpace() {
        int[] clicks = {0};
        Button button = new Button("Go").onClick(() -> clicks[0]++);
        assertTrue(button.keyPressed(GLFW_KEY_ENTER, 0, host));
        assertTrue(button.keyPressed(GLFW_KEY_SPACE, 0, host));
        assertEquals(2, clicks[0]);
    }

    static class Form extends Component {
        Field inner = new Field("inner");
    }

    static class LoginForm extends Screen {
        Text title = new Text("Hi");
        Field user = new Field("user");
        Form form = new Form();
        Button go = new Button("Go");
    }

    @Test
    void tabOrderFollowsDeclarationOrderThroughComponents() {
        LoginForm screen = new LoginForm();
        assertEquals(List.of(screen.user, screen.form.inner, screen.go), screen.focusables());
    }
}
