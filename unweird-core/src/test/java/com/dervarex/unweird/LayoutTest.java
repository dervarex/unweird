package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The layout decisions that don't need a renderer: order, rows, roles and variants. */
class LayoutTest {

    static class Login extends Screen {
        Text heading = new Text("Welcome back");
        Text subtitle = new Text("Sign in");
        Field user = new Field("User");
        Field pass = new Field("Password");
        Button signIn = new Button("Sign in");
        Button forgot = new Button("Forgot?").ghost().below(pass).alignRight(pass);
        Text note = new Text("note").below(pass);
    }

    static class Toolbar extends Screen {
        Button save = new Button("Save");
        Button cancel = new Button("Cancel");
        Button delete = new Button("Delete").danger();
        Text counter = new Text("0");
        Button plus = new Button("+").rightOf(counter);
    }

    static class Section extends Component {
        Text title = new Text("Profile");
        Text hint = new Text("Shown to others");
        Field name = new Field("Name");
    }

    static class WithSection extends Screen {
        Section section = new Section();
    }

    static class Stranger extends Screen {
        Text mine = new Text("mine");
        Text other = new Text("x").below(new Text("not on this screen"));
    }

    static class NotForm extends Screen {
        Widget custom = new Widget() {
            @Override
            protected Size measure(Canvas canvas, float maxWidth) {
                return new Size(0, 0);
            }

            @Override
            protected void draw(Canvas canvas) {
            }
        };
    }

    @Test
    void belowMovesWidgetsKeepingDeclarationOrderPerTarget() {
        Login s = new Login();
        List<Widget> order = Flow.reorder(s, s.children());
        assertEquals(List.of(s.heading, s.subtitle, s.user, s.pass, s.forgot, s.note, s.signIn), order);
    }

    @Test
    void buttonsGroupIntoRowsAndRightOfJoinsTheTargetRow() {
        Toolbar s = new Toolbar();
        List<List<Widget>> rows = Flow.rows(s, Flow.reorder(s, s.children()));
        assertEquals(List.of(
                List.of(s.save, s.cancel, s.delete),
                List.of(s.counter, s.plus)), rows);
    }

    @Test
    void movedButtonsDontJoinAutomaticRows() {
        Login s = new Login();
        List<List<Widget>> rows = Flow.rows(s, Flow.reorder(s, s.children()));
        assertTrue(rows.contains(List.of(s.forgot)), rows.toString());
        assertTrue(rows.contains(List.of(s.signIn)), rows.toString());
    }

    @Test
    void firstButtonInARowIsPrimaryOthersSecondaryExplicitWins() {
        Toolbar s = new Toolbar();
        Flow.resolveButtonVariants(List.of(s.save, s.cancel, s.delete));
        assertEquals(Button.Variant.PRIMARY, s.save.resolved);
        assertEquals(Button.Variant.SECONDARY, s.cancel.resolved);
        assertEquals(Button.Variant.DANGER, s.delete.resolved);
    }

    @Test
    void headingSubtitleAndBodyAreInferred() {
        Login s = new Login();
        s.headingRole = Text.Role.HEADING;
        Flow.inferRoles(s, Flow.reorder(s, s.children()));
        assertEquals(Text.Role.HEADING, s.heading.resolved);
        assertEquals(Text.Role.SUBTITLE, s.subtitle.resolved);
        assertEquals(Text.Role.BODY, s.note.resolved);
    }

    @Test
    void aComponentOpeningTheScreenCarriesTheHeading() {
        WithSection s = new WithSection();
        s.headingRole = Text.Role.HEADING;
        Flow.inferRoles(s, s.children());
        assertEquals(Text.Role.HEADING, s.section.headingRole);
        Flow.inferRoles(s.section, s.section.children());
        assertEquals(Text.Role.HEADING, s.section.title.resolved);
        assertEquals(Text.Role.SUBTITLE, s.section.hint.resolved);
    }

    @Test
    void formLikeLooksThroughComponents() {
        assertTrue(Flow.formLike(new WithSection()));
        assertTrue(Flow.formLike(new Login()));
        assertFalse(Flow.formLike(new NotForm()));
    }

    @Test
    void relationsToWidgetsElsewhereAreReported() {
        Stranger s = new Stranger();
        var error = assertThrows(IllegalStateException.class, () -> Flow.reorder(s, s.children()));
        assertTrue(error.getMessage().contains("isn't in the same screen"), error.getMessage());
    }

    @Test
    void relationsRejectNullAndSelf() {
        Text text = new Text("a");
        assertThrows(IllegalArgumentException.class, () -> text.below(null));
        assertThrows(IllegalArgumentException.class, () -> text.below(text));
        assertThrows(IllegalArgumentException.class, () -> text.gap(-1));
    }
}
