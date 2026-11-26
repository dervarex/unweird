package com.dervarex.examples.settings;

import com.dervarex.unweird.style.Color;

public enum Accent {
    VIOLET("Violet", "#7c6cff"),
    TEAL("Teal", "#14b8a6"),
    AMBER("Amber", "#f59e0b"),
    ROSE("Rose", "#f43f5e");

    final String label;
    final Color color;

    Accent(String label, String hex) {
        this.label = label;
        this.color = Color.hex(hex);
    }
}
