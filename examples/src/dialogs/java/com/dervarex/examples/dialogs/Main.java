package com.dervarex.examples.dialogs;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Dialogs")
                .size(620, 460)
                .theme(Theme.dark())
                .start(DialogsScreen.class);
    }
}
