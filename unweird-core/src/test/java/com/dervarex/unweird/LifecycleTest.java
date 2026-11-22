package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleTest {

    static final List<String> LOG = new ArrayList<>();

    static class Panel extends Component {
        final String name;
        Text text = new Text("panel");

        Panel(String name) {
            this.name = name;
            whileShown(() -> LOG.add("show " + name), () -> LOG.add("hide " + name));
        }
    }

    static class Home extends Screen {
        final List<String> items = new ArrayList<>(List.of("a"));
        Panel panel = new Panel("panel");
        Each<String> rows = new Each<>(() -> items, Panel::new);

        {
            whileShown(() -> LOG.add("show screen"), () -> LOG.add("hide screen"));
        }
    }

    @Test
    void outerFirstWhenShownInnerFirstWhenHidden() {
        LOG.clear();
        Home home = new Home();
        home.update();               // Each creates its rows
        home.attach();
        assertEquals(List.of("show screen", "show panel", "show a"), LOG);
        LOG.clear();
        home.detach();
        assertEquals(List.of("hide panel", "hide a", "hide screen"), LOG);
    }

    @Test
    void attachingTwiceRunsHooksOnce() {
        LOG.clear();
        Home home = new Home();
        home.attach();
        home.attach();
        home.detach();
        home.detach();
        assertEquals(1, LOG.stream().filter("show screen"::equals).count());
        assertEquals(1, LOG.stream().filter("hide screen"::equals).count());
    }

    @Test
    void rowsComingAndGoingWhileShownGetTheirHooks() {
        LOG.clear();
        Home home = new Home();
        home.update();
        home.attach();
        LOG.clear();
        home.items.add("b");
        home.items.remove("a");
        home.update();               // what a frame does
        assertEquals(List.of("hide a", "show b"), LOG);
    }

    @Test
    void registeringWhileShownRunsRightAway() {
        LOG.clear();
        Home home = new Home();
        home.attach();
        LOG.clear();
        home.whileShown(() -> LOG.add("late show"), () -> LOG.add("late hide"));
        home.detach();
        assertEquals(List.of("late show", "hide panel", "late hide", "hide screen"), LOG);
    }

    @Test
    void aThrowingHookDoesntStopTheOthers() {
        LOG.clear();
        Home home = new Home();
        home.whileShown(() -> { throw new IllegalStateException("boom"); }, () -> { });
        home.attach();
        assertEquals(List.of("show screen", "show panel"), LOG.subList(0, 2));
    }

    @Test
    void openUsesTheRightCommandPerOs() {
        assertEquals(List.of("xdg-open", "https://example.com"), Unweird.openCommand("Linux", " https://example.com "));
        assertEquals(List.of("open", "/tmp/a b.png"), Unweird.openCommand("Mac OS X", "/tmp/a b.png"));
        assertEquals(List.of("rundll32", "url.dll,FileProtocolHandler", "mailto:me@example.com"),
                Unweird.openCommand("Windows 11", "mailto:me@example.com"));
        assertThrows(IllegalArgumentException.class, () -> Unweird.openCommand("Linux", "--help"));
        assertThrows(IllegalArgumentException.class, () -> Unweird.openCommand("Linux", "  "));
    }
}
