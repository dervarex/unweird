package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;

class DialogTest {

    static class RenameDialog extends Dialog {
        final List<String> renamed = new ArrayList<>();
        Field name = new Field("New name");

        RenameDialog() {
            super("Rename world");
            action("Rename", () -> {
                if (name.value().isBlank()) {
                    stay();
                    return;
                }
                renamed.add(name.value());
            });
            cancel("Cancel");
        }
    }

    /** Pretends the dialog is on screen (no window in tests). */
    private static ModalLayer open(Dialog dialog) {
        ModalLayer layer = new ModalLayer(dialog);
        dialog.layer = layer;
        return layer;
    }

    @Test
    void fieldsGoBetweenTheMessageAndTheButtons() {
        RenameDialog dialog = new RenameDialog();
        List<Widget> children = dialog.children();
        assertSame(dialog.getTitle(), children.get(0));
        assertSame(dialog.getMessage(), children.get(1));
        assertSame(dialog.name, children.get(2));
        assertTrue(children.get(3) instanceof Dialog.ButtonBar);
        assertEquals(4, children.size());
    }

    @Test
    void actionsCloseUnlessTheyStay() {
        RenameDialog dialog = new RenameDialog();
        open(dialog);
        Button rename = dialog.focusables().stream()
                .filter(w -> w instanceof Button b && b.label().equals("Rename")).map(Button.class::cast).findFirst().orElseThrow();

        rename.click();                       // empty name: stays open
        assertTrue(dialog.isOpen());
        dialog.name.value("Castle");
        rename.click();
        assertFalse(dialog.isOpen());
        assertEquals(List.of("Castle"), dialog.renamed);
    }

    @Test
    void enterRunsTheDefaultActionAndEscapeCancels() {
        List<String> events = new ArrayList<>();
        Dialog dialog = Dialog.confirm("Delete?", "Gone for good.")
                .action("Delete", () -> events.add("delete")).danger()
                .cancel("Keep")
                .onCancel(() -> events.add("cancel"))
                .onClose(() -> events.add("closed"));
        ModalLayer layer = open(dialog);
        assertTrue(layer.keyPressed(GLFW_KEY_ENTER, 0, new FieldTest.FakeHost()));
        assertEquals(List.of("delete", "closed"), events);

        open(dialog);
        layer.escape();
        assertEquals(List.of("delete", "closed", "cancel", "closed"), events);
    }

    @Test
    void focusStartsOnInputsElseOnCancel() {
        assertTrue(new RenameDialog().initialFocus() instanceof Field);
        Dialog confirm = Dialog.confirm("Delete?", "").action("Delete", () -> { }).danger().cancel("Keep");
        assertEquals("Keep", ((Button) confirm.initialFocus()).label());
    }

    @Test
    void buttonsAreOrderedCancelFirstDefaultLast() {
        Dialog dialog = Dialog.confirm("Save changes?", "")
                .action("Save", () -> { })
                .action("Don't save", () -> { })
                .cancel("Cancel");
        dialog.children();
        Dialog.ButtonBar bar = (Dialog.ButtonBar) dialog.children().getLast();
        assertEquals(List.of("Cancel", "Don't save", "Save"), bar.children().stream().map(w -> ((Button) w).label()).toList());
        assertEquals(Button.Variant.PRIMARY, ((Button) bar.children().getLast()).variant);
    }

    @Test
    void dangerNeedsAnAction() {
        assertThrows(IllegalStateException.class, () -> new Dialog("x").danger());
    }

    @Test
    void enterInAFieldWithoutSubmitIsLeftForTheDialog() {
        assertFalse(new Field("x").keyPressed(GLFW_KEY_ENTER, 0, new FieldTest.FakeHost()));
    }
}
