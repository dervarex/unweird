package com.dervarex.unweird.game;

/** Flat ground at one height (default 32): for building worlds. */
public class Flat extends Part {

    int level = 32;

    public Flat() {
    }

    public Flat(int height) {
        level = height;
    }

    @Override
    protected double height(int x, int z, double height) {
        return level;
    }
}
