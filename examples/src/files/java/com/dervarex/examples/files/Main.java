package com.dervarex.examples.files;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Files")
                .size(640, 520)
                .theme(Theme.dark())
                .start(FilesScreen.class);
    }
}
