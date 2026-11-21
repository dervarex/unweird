package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Default;
import com.dervarex.unweird.annotation.Fresh;
import com.dervarex.unweird.annotation.Without;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultsTest {

    @Default(Side.LEFT)
    static class TestSidebar extends Component {
        Button home = new Button("Home");
    }

    @Default(Side.TOP)
    static class TestHeader extends Component {
        Text title = new Text("App");
        Button logout = new Button("Log out");
    }

    static class NotADefault extends Component {
    }

    static class Plain extends Screen {
        Field search = new Field("Search");
    }

    @Without(TestHeader.class)
    static class NoHeader extends Screen {
    }

    static class NoHeaderEither extends NoHeader {
    }

    @Without(NotADefault.class)
    static class WrongWithout extends Screen {
    }

    @Fresh
    static class Wizard extends Screen {
    }

    private static List<Class<?>> dockTypes(Class<? extends Screen> screen) {
        return Defaults.forScreen(screen).stream().<Class<?>>map(slot -> slot.component.getClass()).toList();
    }

    @Test
    void sharedReturnsOneInstance() {
        assertSame(Defaults.shared(TestSidebar.class), Defaults.shared(TestSidebar.class));
    }

    @Test
    void sharedRejectsNonDefaults() {
        var error = assertThrows(IllegalArgumentException.class, () -> Defaults.shared(NotADefault.class));
        assertTrue(error.getMessage().contains("isn't marked @Default"), error.getMessage());
    }

    @Test
    void everyScreenGetsAllDefaultsSortedByName() {
        // DefaultsTest$TestHeader sorts before DefaultsTest$TestSidebar
        assertEquals(List.of(TestHeader.class, TestSidebar.class), dockTypes(Plain.class));
        List<DockSlot> slots = Defaults.forScreen(Plain.class);
        assertEquals(Side.TOP, slots.get(0).side);
        assertEquals(Side.LEFT, slots.get(1).side);
        assertSame(Defaults.shared(TestSidebar.class), slots.get(1).component);
    }

    @Test
    void withoutHidesDefaultsAndIsInherited() {
        assertEquals(List.of(TestSidebar.class), dockTypes(NoHeader.class));
        assertEquals(List.of(TestSidebar.class), dockTypes(NoHeaderEither.class));
    }

    @Test
    void withoutRejectsClassesThatArentDefaults() {
        var error = assertThrows(IllegalStateException.class, () -> Defaults.forScreen(WrongWithout.class));
        assertTrue(error.getMessage().contains("isn't a @Default component"), error.getMessage());
    }

    @Test
    void screensAreCachedUnlessFresh() {
        Screens screens = new Screens();
        assertSame(screens.get(Plain.class), screens.get(Plain.class));
        assertNotSame(screens.get(Wizard.class), screens.get(Wizard.class));
    }

    @Test
    void tabOrderPutsTopAndLeftDocksBeforeContent() {
        Plain screen = new Plain();
        screen.docks = Defaults.forScreen(Plain.class);
        TestHeader header = Defaults.shared(TestHeader.class);
        TestSidebar sidebar = Defaults.shared(TestSidebar.class);
        assertEquals(List.of(header.logout, sidebar.home, screen.search), screen.tabOrder());
    }
}
