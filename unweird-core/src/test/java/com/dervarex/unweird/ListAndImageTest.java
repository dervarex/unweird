package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

class ListAndImageTest {

    private final FieldTest.FakeHost host = new FieldTest.FakeHost();

    @Test
    void arrowsSelectAndOnlyChangesAreReported() {
        List<String> selections = new ArrayList<>();
        ListView<String> list = new ListView<>(List.of("a", "b", "c")).onSelect(selections::add);
        list.keyPressed(GLFW_KEY_DOWN, 0, host);   // nothing selected yet -> first
        list.keyPressed(GLFW_KEY_DOWN, 0, host);
        list.keyPressed(GLFW_KEY_END, 0, host);
        list.keyPressed(GLFW_KEY_DOWN, 0, host);   // already last: no change, no event
        assertEquals("c", list.selected());
        assertEquals(List.of("a", "b", "c"), selections);
    }

    @Test
    void liveItemsAreReReadAndARemovedSelectionIsCleared() {
        List<String> tasks = new ArrayList<>(List.of("write tests", "ship it"));
        ListView<String> list = new ListView<>(() -> tasks);
        list.update();
        list.choose(1, host);
        assertEquals("ship it", list.selected());

        tasks.remove("ship it");
        list.update();
        assertEquals(List.of("write tests"), list.items());
        assertNull(list.selected());
    }

    static class LoginWithLogo extends Screen {
        Image logo = new Image("/missing-logo.png").size(48, 48);
        Text heading = new Text("Welcome back");
        Text subtitle = new Text("Sign in");
        Field user = new Field("User");
    }

    @Test
    void aLogoKeepsTheFormOnACardAndTheHeadingAfterIt() {
        LoginWithLogo s = new LoginWithLogo();
        assertTrue(Flow.formLike(s));
        s.headingRole = Text.Role.HEADING;
        Flow.inferRoles(s, s.children());
        assertEquals(Text.Role.HEADING, s.heading.resolved);
        assertEquals(Text.Role.SUBTITLE, s.subtitle.resolved);
    }

    @Test
    void imageSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new Image("/x.png").size(0, 10));
    }
}
