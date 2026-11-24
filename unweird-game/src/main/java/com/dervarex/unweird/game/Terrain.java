package com.dervarex.unweird.game;

import java.util.List;

/**
 * The ground of a {@link Game3D}, endless in every direction, made of {@link Part}s declared as fields:
 *
 * <pre>{@code
 * class Land extends Terrain {
 *     Hills   hills   = new Hills();
 *     Surface surface = new Surface(grass, dirt, stone);
 *     Sea     sea     = new Sea(water);
 *     Caves   caves   = new Caves();
 *     Trees   trees   = new Trees(log, leaves);
 * }
 * }</pre>
 *
 * Every part can be swapped, left out, tuned ({@code new Hills().height(50)}) or subclassed; see {@link Part} for
 * writing your own.
 */
public abstract class Terrain {

    /** Blocks stacked in a column; y goes from 0 to {@code HEIGHT - 1}. */
    public static final int HEIGHT = Chunk.HEIGHT;

    Noise noise = new Noise(0);
    List<Part> parts;
    /** Water level, if there's a {@link Sea}; -1 otherwise. */
    int seaLevel = -1;
    /** Used when no part says what the ground is made of. */
    Block fallback;

    void prepare(long seed, Block fallback) {
        this.noise = new Noise(seed);
        this.fallback = fallback;
        parts = Members.valuesOf(this, Part.class);
        for (Part part : parts) {
            part.terrain = this;
            if (part instanceof Sea sea) {
                seaLevel = sea.level;
            }
        }
    }

    /** Ground height of a column. */
    int height(int x, int z) {
        double height = 0;
        boolean shaped = false;
        for (Part part : parts) {
            double next = part.height(x, z, height);
            shaped |= next != height;
            height = next;
        }
        if (!shaped) {
            height = 32;
        }
        return Math.max(1, Math.min(HEIGHT - 8, (int) Math.round(height)));
    }

    /** The natural block at a place (before anyone built there), null = air. */
    Block block(int x, int y, int z, int top) {
        if (y > Math.max(top, seaLevel)) {
            return null;
        }
        int depth = top - y;
        Block block = depth >= 0 ? fallback : null;
        boolean made = false;
        for (Part part : parts) {
            Block next = part.block(x, y, z, depth, block);
            if (next != block) {
                made = true;
            }
            block = next;
        }
        return made || depth >= 0 ? block : null;
    }
}
