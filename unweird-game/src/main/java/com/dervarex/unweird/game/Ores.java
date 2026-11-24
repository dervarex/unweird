package com.dervarex.unweird.game;

/** Small lumps of {@code ore} deep in the ground: {@code new Ores(coal)}, {@code new Ores(gold).rare()}. */
public class Ores extends Part {

    final Block ore;
    double amount = 0.012;
    int below = 5;

    public Ores(Block ore) {
        this.ore = ore;
    }

    /** A lot fewer. */
    public Ores rare() {
        amount = 0.003;
        below = 15;
        return this;
    }

    /** More of them. */
    public Ores common() {
        amount = 0.03;
        return this;
    }

    @Override
    protected Block block(int x, int y, int z, int depth, Block block) {
        if (block == null || !block.opaque() || depth < below) {
            return block;
        }
        return chance(x >> 1, y >> 1, z >> 1, amount) && chance(x, y, z, 0.7) ? ore : block;
    }
}
