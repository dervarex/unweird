package com.dervarex.unweird.game;

/** Rolling hills: ground around {@link #height(int) height} 40, rising and falling about 12 blocks. */
public class Hills extends Part {

    int base = 40;
    int rise = 12;
    double size = 90;

    /** The average ground height. */
    public Hills height(int blocks) {
        base = blocks;
        return this;
    }

    /** How far the hills go above and below the average. */
    public Hills rise(int blocks) {
        rise = blocks;
        return this;
    }

    /** How wide one hill is, roughly, in blocks (default 90). */
    public Hills width(double blocks) {
        size = blocks;
        return this;
    }

    @Override
    protected double height(int x, int z, double height) {
        double start = height == 0 ? base : height;
        return start + terrain.noise.layered(x / size, z / size, 4) * rise * 1.6;
    }
}
