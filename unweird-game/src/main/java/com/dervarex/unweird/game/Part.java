package com.dervarex.unweird.game;

/**
 * One step of making a {@link Terrain}: its shape ({@link Hills}, {@link Flat}, {@link Mountains}), what it's made of
 * ({@link Surface}, {@link Sea}, {@link Beach}, {@link Caves}, {@link Ores}, {@link Bottom}) or what grows on it
 * ({@link Trees}, {@link Plants}). Parts run in the order the terrain declares them.
 *
 * <p>For something of your own, extend {@code Part} (or one of the parts) and override what you need:
 * <pre>{@code
 * class Pillars extends Part {
 *     Block block(int x, int y, int z, int depth, Block block) {
 *         return x % 32 == 0 && z % 32 == 0 && depth > -20 ? stone : block;
 *     }
 * }
 * }</pre>
 */
public abstract class Part {

    Terrain terrain;

    /** The ground height of column (x, z), given the height so far (0 before any shape part). */
    protected double height(int x, int z, double height) {
        return height;
    }

    /**
     * The block at (x, y, z), given the block so far (null = air). {@code depth} is how far below the ground it is
     * (0 = the top block, negative = above the ground). Called up to the ground or sea level, whichever is higher.
     */
    protected Block block(int x, int y, int z, int depth, Block block) {
        return block;
    }

    /** Adds things on top of column (x, z) whose ground is at {@code top}: trees, plants, rocks. */
    protected void decorate(int x, int z, int top, Area area) {
    }

    /** Where {@link #decorate} builds. Places outside the chunk being made are ignored (neighbors build those). */
    public interface Area {
        Block get(int x, int y, int z);

        void set(int x, int y, int z, Block block);
    }

    // ---- helpers ----

    /** Smooth noise about -1..1; {@code size} is how wide its bumps are, in blocks. */
    protected final double noise(double x, double z, double size) {
        return terrain.noise.at(x / size, z / size);
    }

    /** Smooth 3D noise about -1..1. */
    protected final double noise(double x, double y, double z, double size) {
        return terrain.noise.at(x / size, y / size, z / size);
    }

    /** True for about {@code probability} of all columns, always the same ones. */
    protected final boolean chance(int x, int z, double probability) {
        return terrain.noise.random(x, getClass().getName().hashCode(), z) < probability;
    }

    /** True for about {@code probability} of all blocks, always the same ones. */
    protected final boolean chance(int x, int y, int z, double probability) {
        return terrain.noise.random(x * 31 + getClass().getName().hashCode(), y, z) < probability;
    }
}
