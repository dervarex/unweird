package com.dervarex.examples.contacts;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Contacts")
                .size(720, 560)
                .theme(Theme.dark())
                .start(ContactsScreen.class);
    }
}
