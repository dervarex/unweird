package com.dervarex.examples.dialogs;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Dialog;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toast;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.time.Duration;

@Title("Dialogs")
public class DialogsScreen extends Screen {

    @State String project = "Untitled";

    Text heading   = new Text(() -> project);
    Text subtitle  = new Text("Dialogs block everything behind them. Enter runs the first action, Escape cancels.");

    Button rename  = new Button("Rename…").onClick(() -> show(new RenameDialog(project, name -> project = name)));
    Button delete  = new Button("Delete…").danger().onClick(this::confirmDelete);

    Text toasts    = new Text("Toasts").title();
    Button info    = new Button("Info").secondary().onClick(() -> toast("Saved"));
    Button success = new Button("Success").secondary().onClick(() -> toast(Toast.success("Uploaded 3 files")));
    Button error   = new Button("Error").secondary().onClick(() -> toast(Toast.error("Couldn't connect")));
    Button longer  = new Button("10 seconds").secondary()
            .onClick(() -> toast(Toast.info("I'll stay a while").lasting(Duration.ofSeconds(10))));

    private void confirmDelete() {
        show(Dialog.confirm("Delete “" + project + "”?", "This can't be undone.")
                .action("Delete", () -> {
                    toast(Toast.success("Deleted " + project));
                    project = "Untitled";
                }).danger()
                .cancel("Keep it"));
    }
}
