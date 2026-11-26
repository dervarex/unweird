package com.dervarex.examples.rating;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Rating")
                .size(520, 380)
                .theme(Theme.dark())
                .start(RatingScreen.class);
    }
}
