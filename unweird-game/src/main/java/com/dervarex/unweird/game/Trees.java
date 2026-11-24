package com.dervarex.unweird.game;

/** Trees on dry ground: a trunk of {@code log} with a crown of {@code leaves}. {@code .dense()} or {@code .sparse()}. */
public class Trees extends Part {

    final Block log;
    final Block leaves;
    double amount = 0.012;

    public Trees(Block log, Block leaves) {
        this.log = log;
        this.leaves = leaves;
    }

    /** A forest. */
    public Trees dense() {
        amount = 0.05;
        return this;
    }

    /** Only a few. */
    public Trees sparse() {
        amount = 0.003;
        return this;
    }

    @Override
    protected void decorate(int x, int z, int top, Area area) {
        if (!chance(x, z, amount) || top <= terrain.seaLevel) {
            return;
        }
        Block ground = area.get(x, top, z);
        if (ground == null || !ground.opaque() || area.get(x, top + 1, z) != null) {
            return;
        }
        int height = 4 + (int) (terrain.noise.random(x, 7, z) * 3);
        for (int dy = height - 2; dy <= height + 1; dy++) {
            int radius = dy >= height ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (radius == 1 || terrain.noise.random(x + dx, top + dy, z + dz) < 0.5)) {
                        continue;
                    }
                    if (area.get(x + dx, top + 1 + dy, z + dz) == null) {
                        area.set(x + dx, top + 1 + dy, z + dz, leaves);
                    }
                }
            }
        }
        for (int dy = 1; dy <= height; dy++) {
            area.set(x, top + dy, z, log);
        }
    }
}
