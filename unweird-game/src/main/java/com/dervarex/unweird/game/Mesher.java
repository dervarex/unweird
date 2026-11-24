package com.dervarex.unweird.game;

import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.style.Color;

/**
 * Turns a chunk into meshes: only faces next to something you can see through, shaded per side with soft shadows in
 * corners (ambient occlusion). Three meshes: solid, see-through (leaves, glass, plants) and liquid. Thread-safe.
 */
final class Mesher {

    /** Reads a block id anywhere in the world (neighbors of the chunk's edge blocks). */
    interface Lookup {
        short id(int x, int y, int z);
    }

    /** Facts about block ids, indexed by id (0 = air). */
    record Kinds(boolean[] opaque, boolean[] seeThrough, boolean[] liquid, boolean[] plant, BlockArt art) {

        static Kinds of(java.util.List<Block> blocks, BlockArt art) {
            int n = blocks.size() + 1;
            boolean[] opaque = new boolean[n], seeThrough = new boolean[n], liquid = new boolean[n], plant = new boolean[n];
            for (Block block : blocks) {
                opaque[block.id] = block.opaque();
                seeThrough[block.id] = block.seeThrough;
                liquid[block.id] = block.liquid;
                plant[block.id] = block.plant;
            }
            return new Kinds(opaque, seeThrough, liquid, plant, art);
        }
    }

    // Faces: normal, the four corners (counter-clockwise from outside), which picture (top/side/bottom), brightness.
    private static final int[][] NORMALS = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};
    private static final int[][][] CORNERS = {
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
            {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
    };
    private static final int[] PICTURE = {BlockArt.TOP, BlockArt.BOTTOM, BlockArt.SIDE, BlockArt.SIDE, BlockArt.SIDE, BlockArt.SIDE};
    private static final float[] SHADE = {1f, 0.55f, 0.82f, 0.82f, 0.68f, 0.68f};
    private static final float[] OCCLUSION = {0.5f, 0.68f, 0.84f, 1f};
    private static final Color[] GRAYS = new Color[101];

    static {
        for (int i = 0; i <= 100; i++) {
            GRAYS[i] = new Color(i / 100f, i / 100f, i / 100f, 1);
        }
    }

    private Mesher() {
    }

    /** {solid, see-through, liquid}; an entry is null when it would be empty. */
    static Mesh[] build(Chunk chunk, Lookup lookup, Kinds kinds) {
        Mesh.Builder solid = Mesh.builder();
        Mesh.Builder cutout = Mesh.builder();
        Mesh.Builder liquid = Mesh.builder();
        int[] counts = new int[3];
        int baseX = chunk.cx * Chunk.SIZE;
        int baseZ = chunk.cz * Chunk.SIZE;
        short[] blocks = chunk.blocks;
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    short id = blocks[Chunk.index(x, y, z)];
                    if (id == 0) {
                        continue;
                    }
                    float[][] uv = kinds.art.uv[id];
                    if (kinds.plant[id]) {
                        cross(cutout, x, y, z, uv[BlockArt.SIDE]);
                        counts[1]++;
                        continue;
                    }
                    boolean isLiquid = kinds.liquid[id];
                    Mesh.Builder target = isLiquid ? liquid : kinds.opaque[id] ? solid : cutout;
                    for (int face = 0; face < 6; face++) {
                        int[] n = NORMALS[face];
                        short neighbor = id(chunk, lookup, x + n[0], y + n[1], z + n[2], baseX, baseZ);
                        if (!visible(id, neighbor, kinds)) {
                            continue;
                        }
                        float lower = 0;
                        if (isLiquid && face != 1) {
                            short above = id(chunk, lookup, x, y + 1, z, baseX, baseZ);
                            lower = kinds.liquid[above] ? 0 : 0.12f; // a water surface sits a little lower
                        }
                        face(target, chunk, lookup, kinds, x, y, z, face, uv[PICTURE[face]], !isLiquid, lower, baseX, baseZ);
                        counts[isLiquid ? 2 : kinds.opaque[id] ? 0 : 1]++;
                    }
                }
            }
        }
        return new Mesh[]{counts[0] > 0 ? solid.build() : null, counts[1] > 0 ? cutout.build() : null, counts[2] > 0 ? liquid.build() : null};
    }

    /** Whether a block's face towards {@code neighbor} can be seen. */
    static boolean visible(short id, short neighbor, Kinds kinds) {
        if (neighbor == SOLID) {
            return false;
        }
        if (neighbor == 0 || kinds.plant[neighbor]) {
            return true;
        }
        if (kinds.opaque[neighbor]) {
            return false;
        }
        if (neighbor == id) {
            return false; // water next to water, glass next to glass
        }
        if (kinds.liquid[id]) {
            return !kinds.liquid[neighbor] && !kinds.opaque[neighbor];
        }
        return true;
    }

    private static short id(Chunk chunk, Lookup lookup, int x, int y, int z, int baseX, int baseZ) {
        if (y < 0) {
            return SOLID;
        }
        if (y >= Chunk.HEIGHT) {
            return 0;
        }
        if (x >= 0 && x < Chunk.SIZE && z >= 0 && z < Chunk.SIZE) {
            return chunk.blocks[Chunk.index(x, y, z)];
        }
        return lookup.id(baseX + x, y, baseZ + z);
    }

    /** Below the world, or a chunk that isn't there: counts as solid, so no faces are drawn towards it. */
    static final short SOLID = Short.MAX_VALUE;

    private static boolean occludes(short id, Kinds kinds) {
        return id == SOLID || id > 0 && id < kinds.opaque.length && kinds.opaque[id];
    }

    private static void face(Mesh.Builder builder, Chunk chunk, Lookup lookup, Kinds kinds, int x, int y, int z, int face,
                             float[] uv, boolean shadows, float lower, int baseX, int baseZ) {
        int[] n = NORMALS[face];
        int[][] corners = CORNERS[face];
        int[] ao = new int[4];
        int[] index = new int[4];
        for (int i = 0; i < 4; i++) {
            int[] c = corners[i];
            ao[i] = shadows ? occlusion(chunk, lookup, kinds, x, y, z, n, c, baseX, baseZ) : 3;
            float brightness = SHADE[face] * OCCLUSION[ao[i]];
            float cy = c[1] == 1 ? 1 - lower : 0;
            float u;
            float v;
            if (face == 0 || face == 1) {
                u = c[0] == 0 ? uv[0] : uv[2];
                v = c[2] == 0 ? uv[1] : uv[3];
            } else {
                u = i == 0 || i == 3 ? uv[0] : uv[2];
                v = c[1] == 1 ? uv[1] : uv[3];
            }
            index[i] = builder.vertex(x + c[0], y + cy, z + c[2], n[0], n[1], n[2], u, v, GRAYS[Math.round(brightness * 100)]);
        }
        if (ao[0] + ao[2] < ao[1] + ao[3]) { // split along the brighter diagonal, so shadows stay smooth
            builder.triangle(index[1], index[2], index[3]);
            builder.triangle(index[1], index[3], index[0]);
        } else {
            builder.triangle(index[0], index[1], index[2]);
            builder.triangle(index[0], index[2], index[3]);
        }
    }

    /** 0 (dark corner) .. 3 (open), from the blocks around a corner in front of the face. */
    private static int occlusion(Chunk chunk, Lookup lookup, Kinds kinds, int x, int y, int z, int[] n, int[] corner, int baseX, int baseZ) {
        int fx = x + n[0], fy = y + n[1], fz = z + n[2];
        int[] a = new int[3];
        int[] b = new int[3];
        int found = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (n[axis] != 0) {
                continue;
            }
            int[] target = found == 0 ? a : b;
            target[axis] = corner[axis] == 1 ? 1 : -1;
            found++;
        }
        boolean side1 = occludes(id(chunk, lookup, fx + a[0], fy + a[1], fz + a[2], baseX, baseZ), kinds);
        boolean side2 = occludes(id(chunk, lookup, fx + b[0], fy + b[1], fz + b[2], baseX, baseZ), kinds);
        if (side1 && side2) {
            return 0;
        }
        boolean diagonal = occludes(id(chunk, lookup, fx + a[0] + b[0], fy + a[1] + b[1], fz + a[2] + b[2], baseX, baseZ), kinds);
        return 3 - ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (diagonal ? 1 : 0));
    }

    /** Two crossed pictures through the block, for plants. */
    private static void cross(Mesh.Builder builder, int x, int y, int z, float[] uv) {
        float[][][] quads = {
                {{0.15f, 0, 0.15f}, {0.85f, 0, 0.85f}, {0.85f, 1, 0.85f}, {0.15f, 1, 0.15f}},
                {{0.85f, 0, 0.15f}, {0.15f, 0, 0.85f}, {0.15f, 1, 0.85f}, {0.85f, 1, 0.15f}},
        };
        Color color = GRAYS[90];
        for (float[][] q : quads) {
            int a = builder.vertex(x + q[0][0], y + q[0][1], z + q[0][2], 0, 1, 0, uv[0], uv[3], color);
            int b = builder.vertex(x + q[1][0], y + q[1][1], z + q[1][2], 0, 1, 0, uv[2], uv[3], color);
            int c = builder.vertex(x + q[2][0], y + q[2][1], z + q[2][2], 0, 1, 0, uv[2], uv[1], color);
            int d = builder.vertex(x + q[3][0], y + q[3][1], z + q[3][2], 0, 1, 0, uv[0], uv[1], color);
            builder.triangle(a, b, c);
            builder.triangle(a, c, d);
        }
    }
}
