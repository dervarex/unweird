package com.dervarex.examples.search;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Search")
                .size(680, 620)
                .theme(Theme.dark())
                .start(SearchScreen.class);
    }
}
