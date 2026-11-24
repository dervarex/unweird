package com.dervarex.unweird.game;

/**
 * A box that moves through the block world: gravity, swimming, stopping at solid blocks. Position is the center of
 * its feet. UI thread only.
 */
final class Body {

    static final double GRAVITY = 28;
    private static final double EPSILON = 1e-4;

    double x, y, z;
    double vx, vy, vz;
    double width = 0.6;
    double height = 1.8;
    boolean onGround;
    boolean inLiquid;
    boolean bumped;

    /** Moves by its velocity for {@code dt} seconds. */
    void move(Voxels world, double dt, boolean gravity) {
        inLiquid = world.liquid(floor(x), floor(y + 0.4), floor(z));
        if (gravity) {
            vy -= (inLiquid ? GRAVITY * 0.25 : GRAVITY) * dt;
            vy = Math.max(vy, inLiquid ? -3 : -60);
        }
        bumped = false;
        axis(world, 0, vx * dt);
        axis(world, 2, vz * dt);
        onGround = false;
        axis(world, 1, vy * dt);
    }

    /** Moves along one axis in small steps, stopping at the first solid block. */
    private void axis(Voxels world, int axis, double distance) {
        while (distance != 0) {
            double step = Math.max(-0.4, Math.min(0.4, distance));
            distance -= step;
            if (axis == 0) x += step;
            else if (axis == 1) y += step;
            else z += step;
            if (!collides(world)) {
                continue;
            }
            // back out to the edge of the block that was hit
            if (axis == 0) {
                x = step > 0 ? Math.floor(x + width / 2) - width / 2 - EPSILON : Math.floor(x - width / 2) + 1 + width / 2 + EPSILON;
                vx = 0;
                bumped = true;
            } else if (axis == 2) {
                z = step > 0 ? Math.floor(z + width / 2) - width / 2 - EPSILON : Math.floor(z - width / 2) + 1 + width / 2 + EPSILON;
                vz = 0;
                bumped = true;
            } else {
                if (step < 0) {
                    y = Math.floor(y) + 1 + EPSILON;
                    onGround = true;
                } else {
                    y = Math.floor(y + height) - height - EPSILON;
                }
                vy = 0;
            }
            return;
        }
    }

    /** Whether it overlaps a solid block right now. */
    boolean collides(Voxels world) {
        return collides(world, x, y, z);
    }

    boolean collides(Voxels world, double atX, double atY, double atZ) {
        int x0 = floor(atX - width / 2), x1 = floor(atX + width / 2 - EPSILON);
        int y0 = floor(atY), y1 = floor(atY + height - EPSILON);
        int z0 = floor(atZ - width / 2), z1 = floor(atZ + width / 2 - EPSILON);
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (world.solid(bx, by, bz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether the block at (bx, by, bz) overlaps this body. */
    boolean overlapsBlock(int bx, int by, int bz) {
        return x + width / 2 > bx && x - width / 2 < bx + 1 && y + height > by && y < by + 1 && z + width / 2 > bz && z - width / 2 < bz + 1;
    }

    /** Distance along a ray (from o in direction d) to this body's box, or -1 if it misses. */
    double ray(double ox, double oy, double oz, double dx, double dy, double dz) {
        double[] min = {x - width / 2, y, z - width / 2};
        double[] max = {x + width / 2, y + height, z + width / 2};
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        double near = 0, far = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-12) {
                if (o[i] < min[i] || o[i] > max[i]) {
                    return -1;
                }
                continue;
            }
            double t1 = (min[i] - o[i]) / d[i];
            double t2 = (max[i] - o[i]) / d[i];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) {
                return -1;
            }
        }
        return near;
    }

    static int floor(double v) {
        return (int) Math.floor(v);
    }
}
