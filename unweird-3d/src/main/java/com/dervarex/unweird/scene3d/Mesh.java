package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.style.Color;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Triangles with normals, texture coordinates and vertex colors. Immutable; build with {@link #builder()} or use
 * {@link #box}, {@link #plane}. Y is up, the camera looks along -Z, front faces wind counter-clockwise.
 *
 * <p>Uploading to the GPU happens on first draw; a mesh nobody draws any more is freed after a while,
 * so rebuilding meshes (e.g. a voxel world after an edit) is fine.
 */
public final class Mesh {

    /** Floats per vertex: position 3, normal 3, uv 2, color 4. */
    static final int STRIDE = 12;

    final float[] vertices;
    final int[] indices;
    private final Vector3f min;
    private final Vector3f max;

    private Mesh(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
        Vector3f lo = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f hi = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int i = 0; i < vertices.length; i += STRIDE) {
            lo.min(new Vector3f(vertices[i], vertices[i + 1], vertices[i + 2]));
            hi.max(new Vector3f(vertices[i], vertices[i + 1], vertices[i + 2]));
        }
        this.min = vertices.length == 0 ? new Vector3f() : lo;
        this.max = vertices.length == 0 ? new Vector3f() : hi;
    }

    public int vertexCount() {
        return vertices.length / STRIDE;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    /** Smallest corner of the bounding box. */
    public Vector3f min() {
        return new Vector3f(min);
    }

    /** Largest corner of the bounding box. */
    public Vector3f max() {
        return new Vector3f(max);
    }

    Vector3f position(int vertex) {
        int i = vertex * STRIDE;
        return new Vector3f(vertices[i], vertices[i + 1], vertices[i + 2]);
    }

    // ---- ready-made shapes ----

    /** A box centered on the origin; each face shows the whole texture. */
    public static Mesh box(float width, float height, float depth) {
        return builder().box(0, 0, 0, width, height, depth, null).build();
    }

    /** A box centered on the origin with texture coordinates from a {@link BoxUv} layout (pixel art, skins). */
    public static Mesh box(float width, float height, float depth, BoxUv uv) {
        return builder().box(0, 0, 0, width, height, depth, uv).build();
    }

    /** A flat square on the XZ plane, facing up. */
    public static Mesh plane(float width, float depth) {
        Builder builder = builder();
        float w = width / 2;
        float d = depth / 2;
        builder.quad(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0),
                w, d, 0, 0, 1, 1, Color.WHITE);
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Texture coordinates for a box in the common "box UV" layout (Blockbench, Minecraft skins): for a box of
     * {@code w × h × d} texture pixels starting at ({@code u}, {@code v}), the top and bottom sit in the first row,
     * the four sides in the row below: right, front, left, back.
     */
    public record BoxUv(float u, float v, float w, float h, float d, float textureWidth, float textureHeight) {

        public static BoxUv of(float u, float v, float w, float h, float d, float textureWidth, float textureHeight) {
            return new BoxUv(u, v, w, h, d, textureWidth, textureHeight);
        }

        /** {x0, y0, x1, y1} in 0..1 for one face. */
        float[] face(Face face) {
            float x;
            float y;
            float fw;
            float fh;
            switch (face) {
                case TOP -> { x = u + d; y = v; fw = w; fh = d; }
                case BOTTOM -> { x = u + d + w; y = v; fw = w; fh = d; }
                case RIGHT -> { x = u; y = v + d; fw = d; fh = h; }
                case FRONT -> { x = u + d; y = v + d; fw = w; fh = h; }
                case LEFT -> { x = u + d + w; y = v + d; fw = d; fh = h; }
                default -> { x = u + d + w + d; y = v + d; fw = w; fh = h; } // BACK
            }
            // a hair inside the face's pixels, so sampling exactly on the edge never picks the neighbouring texel
            float inset = 0.001f;
            return new float[]{(x + inset) / textureWidth, (y + inset) / textureHeight,
                    (x + fw - inset) / textureWidth, (y + fh - inset) / textureHeight};
        }
    }

    /** The faces of a box. FRONT faces +Z (the viewer), RIGHT is the box's own right (-X when it faces you). */
    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    /** Collects vertices and triangles. */
    public static final class Builder {

        private float[] vertices = new float[STRIDE * 64];
        private int[] indices = new int[96];
        private int vertexFloats;
        private int indexCount;

        /** Adds one vertex and returns its index. */
        public int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, Color color) {
            if (vertexFloats + STRIDE > vertices.length) {
                vertices = Arrays.copyOf(vertices, vertices.length * 2);
            }
            float[] a = vertices;
            int i = vertexFloats;
            a[i] = x; a[i + 1] = y; a[i + 2] = z;
            a[i + 3] = nx; a[i + 4] = ny; a[i + 5] = nz;
            a[i + 6] = u; a[i + 7] = v;
            a[i + 8] = color.r(); a[i + 9] = color.g(); a[i + 10] = color.b(); a[i + 11] = color.a();
            vertexFloats += STRIDE;
            return i / STRIDE;
        }

        public Builder triangle(int a, int b, int c) {
            if (indexCount + 3 > indices.length) {
                indices = Arrays.copyOf(indices, indices.length * 2);
            }
            indices[indexCount++] = a;
            indices[indexCount++] = b;
            indices[indexCount++] = c;
            return this;
        }

        /**
         * A rectangle around {@code center}, spanning ±{@code halfRight} along {@code right} and ±{@code halfUp}
         * along {@code up}, facing {@code normal}. Texture coordinates go from (u0, v0) at the top-left to (u1, v1).
         */
        public Builder quad(Vector3f center, Vector3f right, Vector3f up, Vector3f normal, float halfRight, float halfUp,
                            float u0, float v0, float u1, float v1, Color color) {
            Vector3f r = new Vector3f(right).mul(halfRight);
            Vector3f u = new Vector3f(up).mul(halfUp);
            Vector3f bl = new Vector3f(center).sub(r).sub(u);
            Vector3f br = new Vector3f(center).add(r).sub(u);
            Vector3f tr = new Vector3f(center).add(r).add(u);
            Vector3f tl = new Vector3f(center).sub(r).add(u);
            int a = vertex(bl.x, bl.y, bl.z, normal.x, normal.y, normal.z, u0, v1, color);
            int b = vertex(br.x, br.y, br.z, normal.x, normal.y, normal.z, u1, v1, color);
            int c = vertex(tr.x, tr.y, tr.z, normal.x, normal.y, normal.z, u1, v0, color);
            int d = vertex(tl.x, tl.y, tl.z, normal.x, normal.y, normal.z, u0, v0, color);
            triangle(a, b, c);
            triangle(a, c, d);
            return this;
        }

        /** A box centered at (cx, cy, cz). {@code uv} may be null (every face shows the whole texture). */
        public Builder box(float cx, float cy, float cz, float width, float height, float depth, BoxUv uv) {
            return box(cx, cy, cz, width, height, depth, uv, Color.WHITE, Face.values());
        }

        Builder box(float cx, float cy, float cz, float width, float height, float depth, BoxUv uv, Color color, Face... faces) {
            float w = width / 2;
            float h = height / 2;
            float d = depth / 2;
            for (Face face : faces) {
                float[] t = uv == null ? new float[]{0, 0, 1, 1} : uv.face(face);
                switch (face) {
                    case FRONT -> quad(new Vector3f(cx, cy, cz + d), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1), w, h, t[0], t[1], t[2], t[3], color);
                    case BACK -> quad(new Vector3f(cx, cy, cz - d), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1), w, h, t[0], t[1], t[2], t[3], color);
                    case LEFT -> quad(new Vector3f(cx + w, cy, cz), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), d, h, t[0], t[1], t[2], t[3], color);
                    case RIGHT -> quad(new Vector3f(cx - w, cy, cz), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0), new Vector3f(-1, 0, 0), d, h, t[0], t[1], t[2], t[3], color);
                    case TOP -> quad(new Vector3f(cx, cy + h, cz), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0), w, d, t[0], t[1], t[2], t[3], color);
                    case BOTTOM -> quad(new Vector3f(cx, cy - h, cz), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, -1, 0), w, d, t[0], t[1], t[2], t[3], color);
                }
            }
            return this;
        }

        /** One face of a unit cube at block coordinates (x, y, z), for voxel meshes. */
        public Builder blockFace(int x, int y, int z, BlockFace face, Color color) {
            Face f = switch (face) {
                case UP -> Face.TOP;
                case DOWN -> Face.BOTTOM;
                case NORTH -> Face.BACK;
                case SOUTH -> Face.FRONT;
                case EAST -> Face.LEFT;
                case WEST -> Face.RIGHT;
            };
            return box(x + 0.5f, y + 0.5f, z + 0.5f, 1, 1, 1, null, color, f);
        }

        public Mesh build() {
            return new Mesh(Arrays.copyOf(vertices, vertexFloats), Arrays.copyOf(indices, indexCount));
        }
    }

    /** The six faces of a block, by the direction they face: UP +Y, DOWN -Y, NORTH -Z, SOUTH +Z, EAST +X, WEST -X. */
    public enum BlockFace {
        UP(0, 1, 0), DOWN(0, -1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0), WEST(-1, 0, 0);

        public final int dx;
        public final int dy;
        public final int dz;

        BlockFace(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
