package com.dervarex.examples.cube;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Cube")
                .size(760, 620)
                .theme(Theme.dark())
                .start(CubeScreen.class);
    }
}
