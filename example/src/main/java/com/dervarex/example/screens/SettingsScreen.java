package com.dervarex.example.screens;

import com.dervarex.example.components.AccountSection;
import com.dervarex.example.components.AppearanceSection;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Fresh;
import com.dervarex.unweird.annotation.Title;

@Title("Settings")
@Fresh
public class SettingsScreen extends Screen {

    Text heading = new Text("Settings");
    AppearanceSection appearance = new AppearanceSection();
    AccountSection account = new AccountSection();
}
