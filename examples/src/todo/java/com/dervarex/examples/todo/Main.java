package com.dervarex.examples.todo;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Todo")
                .size(620, 560)
                .theme(Theme.dark())
                .start(TodoScreen.class);
    }
}
