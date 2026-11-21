package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Skip;
import com.dervarex.unweird.annotation.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoAddTest {

    static class Login extends Screen {
        Text heading = new Text("Welcome back");
        @Skip Text hidden = new Text("not shown");
        Button signIn = new Button("Sign in");
        String notAWidget = "ignored";
        static Text shared = new Text("static fields are ignored");
        Text footer = new Text("footer");
    }

    static class ExtendedLogin extends Login {
        Text extra = new Text("extra");
    }

    static class Manual extends Screen {
        Text a = new Text("a");
        Text b = new Text("b");

        Manual() {
            add(b);
        }
    }

    static class Panel extends Component {
        Button inside = new Button("inside");
    }

    static class WithPanel extends Screen {
        Panel panel = new Panel();
    }

    static class Counter extends Screen {
        @State int clicks = 0;
        Text label = new Text(() -> "Clicked " + clicks);
        Button plus = new Button("+").onClick(() -> clicks++);
    }

    static class Twice extends Screen {
        Text text = new Text("same");
        Text again = text;
    }

    @Test
    void addsWidgetFieldsInDeclarationOrder() {
        Login login = new Login();
        assertEquals(List.of(login.heading, login.signIn, login.footer), login.children());
    }

    @Test
    void superclassFieldsComeFirst() {
        ExtendedLogin login = new ExtendedLogin();
        assertEquals(List.of(login.heading, login.signIn, login.footer, login.extra), login.children());
    }

    @Test
    void manualAddTurnsAutoAddingOff() {
        Manual manual = new Manual();
        assertEquals(List.of(manual.b), manual.children());
    }

    @Test
    void componentsResolveTheirOwnChildren() {
        WithPanel screen = new WithPanel();
        assertEquals(List.of(screen.panel), screen.children());
        assertEquals(List.of(screen.panel.inside), screen.panel.children());
        assertSame(screen, screen.panel.parent);
    }

    @Test
    void anonymousClassesGetAHelpfulError() {
        Screen anonymous = new Screen() {
            final Text text = new Text("hi");
        };
        var error = assertThrows(IllegalStateException.class, anonymous::children);
        assertTrue(error.getMessage().contains("add(...)"), error.getMessage());
    }

    @Test
    void sameWidgetTwiceIsRejected() {
        var error = assertThrows(IllegalStateException.class, () -> new Twice().children());
        assertTrue(error.getMessage().contains("one place"), error.getMessage());
    }

    @Test
    void lambdasSeeStateChangedByClicks() {
        Counter counter = new Counter();
        counter.update();
        assertEquals("Clicked 0", counter.label.text());

        counter.plus.click();
        counter.plus.click();
        counter.update();
        assertEquals("Clicked 2", counter.label.text());
    }

    @Test
    void settingTextReplacesTheLambda() {
        Counter counter = new Counter();
        counter.label.text("fixed");
        counter.plus.click();
        counter.update();
        assertEquals("fixed", counter.label.text());
    }
}
