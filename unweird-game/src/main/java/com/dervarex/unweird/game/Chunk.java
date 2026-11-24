package com.dervarex.unweird.game;

import com.dervarex.unweird.scene3d.Node;

/** 16 × 128 × 16 blocks, stored as block ids (0 = air). */
final class Chunk {

    static final int SIZE = 16;
    static final int HEIGHT = 128;

    final int cx;
    final int cz;
    final short[] blocks = new short[SIZE * HEIGHT * SIZE];

    /** The scene nodes showing it: solid, see-through, liquid. Null until meshed. UI thread. */
    Node solid;
    Node cutout;
    Node liquid;
    boolean meshing;
    boolean meshed;
    /** Changed since its mesh was made. */
    boolean dirty;

    Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    short get(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    void set(int x, int y, int z, short id) {
        blocks[index(x, y, z)] = id;
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    long key() {
        return key(cx, cz);
    }
}
