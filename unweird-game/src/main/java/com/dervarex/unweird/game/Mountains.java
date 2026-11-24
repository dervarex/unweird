package com.dervarex.unweird.game;

/** Tall, sharp mountains in some places, added on top of the shape so far (put it after {@link Hills}). */
public class Mountains extends Part {

    int peak = 45;

    /** How high the tallest peaks rise above the ground around them. */
    public Mountains height(int blocks) {
        peak = blocks;
        return this;
    }

    @Override
    protected double height(int x, int z, double height) {
        double start = height == 0 ? 40 : height;
        double region = terrain.noise.layered(x / 400.0 + 31, z / 400.0 - 17, 2); // where mountains are
        if (region < 0.05) {
            return start;
        }
        double ridge = 1 - Math.abs(terrain.noise.layered(x / 120.0 - 7, z / 120.0 + 3, 4));
        double strength = Math.min(1, (region - 0.05) * 5);
        return start + ridge * ridge * peak * strength;
    }
}
