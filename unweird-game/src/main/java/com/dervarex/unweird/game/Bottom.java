package com.dervarex.unweird.game;

/** A floor at the very bottom that can't be dug through (mark the block {@link Unbreakable @Unbreakable}). */
public class Bottom extends Part {

    final Block floor;

    public Bottom(Block floor) {
        this.floor = floor;
    }

    @Override
    protected Block block(int x, int y, int z, int depth, Block block) {
        return y == 0 ? floor : block;
    }
}
