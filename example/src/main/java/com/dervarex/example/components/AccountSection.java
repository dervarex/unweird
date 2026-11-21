package com.dervarex.example.components;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Dialog;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toast;
import com.dervarex.unweird.annotation.State;

public class AccountSection extends Component {

    @State String status = "";

    Text title    = new Text("Account");
    Text hint     = new Text("Forgotten when you leave: the settings screen is @Fresh.");
    Field nick    = new Field("Nickname").onSubmit(this::save);
    Button save   = new Button("Save").onClick(this::save);
    Button delete = new Button("Delete account").danger().onClick(() -> show(
            Dialog.confirm("Delete your account?", "Your tasks and settings will be gone. This can't be undone.")
                    .action("Delete account", () -> toast(Toast.success("Deleted. (Not really, it's a demo.)"))).danger()
                    .cancel("Keep my account")));
    Text result   = new Text(() -> status);

    private void save() {
        status = nick.value().isBlank() ? "Nothing to save." : "Saved \"" + nick.value() + "\".";
    }
}
