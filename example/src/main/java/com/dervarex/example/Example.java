package com.dervarex.example;

import com.dervarex.example.screens.LoginScreen;
import com.dervarex.unweird.Unweird;

public class Example {

    public static void main(String[] args) {
        Unweird.shortcut("Ctrl+Q", Unweird::quit);
        Unweird.start(LoginScreen.class);
    }
}
