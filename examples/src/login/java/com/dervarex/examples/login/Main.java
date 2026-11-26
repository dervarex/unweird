package com.dervarex.examples.login;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Login")
                .size(760, 520)
                .theme(Theme.dark())
                .start(LoginScreen.class);
    }
}
