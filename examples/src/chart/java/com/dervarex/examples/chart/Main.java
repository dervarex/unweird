package com.dervarex.examples.chart;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Chart")
                .size(720, 520)
                .theme(Theme.dark())
                .start(ChartScreen.class);
    }
}
