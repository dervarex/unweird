package com.dervarex.unweird.game;

/** Fills everything below the water level (default 34) that's still air: oceans and lakes. */
public class Sea extends Part {

    final Block water;
    int level = 34;

    public Sea(Block water) {
        this.water = water;
    }

    /** The height of the water surface. */
    public Sea level(int y) {
        level = y;
        if (terrain != null) {
            terrain.seaLevel = y;
        }
        return this;
    }

    @Override
    protected Block block(int x, int y, int z, int depth, Block block) {
        return block == null && y <= level ? water : block;
    }
}
