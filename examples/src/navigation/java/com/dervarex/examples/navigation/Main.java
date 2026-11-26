package com.dervarex.examples.navigation;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Mail")
                .size(900, 560)
                .theme(Theme.dark())
                .start(InboxScreen.class);
    }
}
