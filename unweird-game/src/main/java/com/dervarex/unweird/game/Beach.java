package com.dervarex.unweird.game;

/** Turns the ground near the water's edge into {@code sand} (put it after {@link Surface} and {@link Sea}). */
public class Beach extends Part {

    final Block sand;

    public Beach(Block sand) {
        this.sand = sand;
    }

    @Override
    protected Block block(int x, int y, int z, int depth, Block block) {
        int sea = terrain.seaLevel;
        if (sea < 0 || depth < 0 || depth > 3 || block == null || !block.solid()) {
            return block;
        }
        int top = y + depth;
        return top >= sea - 3 && top <= sea + 1 ? sand : block;
    }
}
