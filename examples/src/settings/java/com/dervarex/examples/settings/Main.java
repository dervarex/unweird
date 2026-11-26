package com.dervarex.examples.settings;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Settings")
                .size(640, 720)
                .theme(Theme.dark())
                .start(SettingsScreen.class);
    }
}
