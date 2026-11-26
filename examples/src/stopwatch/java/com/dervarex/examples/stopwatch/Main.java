package com.dervarex.examples.stopwatch;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Stopwatch")
                .size(520, 560)
                .theme(Theme.dark())
                .start(StopwatchScreen.class);
    }
}
