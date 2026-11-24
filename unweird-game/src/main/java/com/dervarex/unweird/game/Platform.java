package com.dervarex.unweird.game;

/** A wall you can jump up through from below and stand on from above. */
public class Platform extends Wall {

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.PLATFORM;
    }
}
