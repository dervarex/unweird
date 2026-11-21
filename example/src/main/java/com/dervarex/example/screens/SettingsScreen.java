package com.dervarex.example.screens;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Fresh;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

@Title("Settings")
@Fresh
public class SettingsScreen extends Screen {

    @State String saved = "";

    Text heading  = new Text("Settings");
    Text subtitle = new Text("This screen is @Fresh: leave it and everything here is forgotten.");
    Field nick    = new Field("Nickname").onSubmit(this::save);
    Button save   = new Button("Save").onClick(this::save);
    Text status   = new Text(() -> saved);

    private void save() {
        saved = nick.value().isBlank() ? "Nothing to save." : "Saved \"" + nick.value() + "\" (until you leave).";
    }
}
