package com.dervarex.unweird.game;

/**
 * What the ground is made of: {@code top} at the surface, {@code under} for the next few blocks, {@code rest} below.
 * Under water the top is {@code under} too. {@code new Surface(grass, dirt, stone)}.
 */
public class Surface extends Part {

    final Block top;
    final Block under;
    final Block rest;
    int depth = 3;

    public Surface(Block top, Block under, Block rest) {
        this.top = top;
        this.under = under;
        this.rest = rest;
    }

    /** One block everywhere: {@code new Surface(sand)}. */
    public Surface(Block all) {
        this(all, all, all);
    }

    /** How many blocks of {@code under} lie below the top (default 3). */
    public Surface depth(int blocks) {
        depth = blocks;
        return this;
    }

    @Override
    protected Block block(int x, int y, int z, int depth, Block block) {
        if (depth < 0) {
            return block;
        }
        if (depth == 0) {
            return terrain.seaLevel >= 0 && y < terrain.seaLevel ? under : top;
        }
        return depth <= this.depth ? under : rest;
    }
}
