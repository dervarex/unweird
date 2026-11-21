package com.dervarex.example;

import com.dervarex.example.screens.LoginScreen;
import com.dervarex.unweird.Unweird;

public class Example {

    public static void main(String[] args) {
        Unweird.shortcut("Ctrl+Q", Unweird::quit);
        Unweird.app("unweird demo")
                .size(1000, 680).minSize(760, 480)
                .icon("/images/logo.png")
                .remember()
                .start(LoginScreen.class);
    }
}
