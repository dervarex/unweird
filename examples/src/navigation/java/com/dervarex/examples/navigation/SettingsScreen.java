package com.dervarex.examples.navigation;

import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;
import com.dervarex.unweird.annotation.Fresh;
import com.dervarex.unweird.annotation.Title;

/** @Fresh: a new instance every time you navigate here. */
@Title("Settings")
@Fresh
public class SettingsScreen extends Screen {

    Text heading  = new Text("Settings");
    Text subtitle = new Text("This screen is @Fresh: leave and come back, and it starts over.");
    Toggle notify = new Toggle("Notifications");
    Toggle sounds = new Toggle("Sounds").enabledIf(() -> notify.checked());
}
