package com.dervarex.examples.settings;

import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

@Title("Settings")
public class SettingsScreen extends Screen {

    Text heading = new Text("Settings");
    AppearanceSection appearance = new AppearanceSection();
    NotificationsSection notifications = new NotificationsSection();
}
