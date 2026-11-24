package com.dervarex.unweird.game;

/** Winding tunnels under the ground, sometimes opening at the surface. */
public class Caves extends Part {

    double width = 0.09;

    /** Wider (above 1) or narrower (below 1) tunnels. */
    public Caves size(double factor) {
        width = 0.09 * factor;
        return this;
    }

    @Override
    protected Block block(int x, int y, int z, int depth, Block block) {
        if (block == null || y < 3 || depth < 0 || block.liquid || block.unbreakable) {
            return block;
        }
        if (depth < 4 && !chance(x >> 3, z >> 3, 0.15)) {
            return block; // most tunnels stay under the surface
        }
        if (terrain.seaLevel >= 0 && y + depth <= terrain.seaLevel + 1 && depth < 6) {
            return block; // don't drain the sea floor
        }
        double a = terrain.noise.at(x / 40.0, y / 26.0, z / 40.0);
        double b = terrain.noise.at(x / 40.0 + 100, y / 26.0 - 50, z / 40.0 + 70);
        return Math.abs(a) < width && Math.abs(b) < width ? null : block;
    }
}
