package com.dervarex.examples.signup;

import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

public class Main {
    public static void main(String[] args) {
        Unweird.app("Sign up")
                .size(640, 640)
                .theme(Theme.dark())
                .start(SignUpScreen.class);
    }
}
