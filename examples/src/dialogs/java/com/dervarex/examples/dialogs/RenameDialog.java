package com.dervarex.examples.dialogs;

import com.dervarex.unweird.Dialog;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;

import java.util.function.Consumer;

/** A dialog with a form: declare fields like on a screen, add actions in the constructor. */
public class RenameDialog extends Dialog {

    @State String error = "";

    Field name = new Field("New name");
    Text problem = new Text(() -> error).muted();

    RenameDialog(String current, Consumer<String> rename) {
        super("Rename project");
        name.value(current);
        action("Rename", () -> {
            if (name.value().isBlank()) {
                error = "A name, please.";
                stay(); // keep the dialog open
                return;
            }
            rename.accept(name.value().strip());
        });
        cancel("Cancel");
    }
}
