package com.dervarex.examples.counter;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Counter")
                .size(480, 340)
                .theme(Theme.dark())
                .start(CounterScreen.class);
    }
}
