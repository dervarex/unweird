package com.dervarex.unweird.game;

/** Walks a ray through the block grid to the first block it hits (liquids are looked through). */
final class Raycast {

    /** The block that was hit and the empty place in front of it (where a new block would go). */
    record Hit(int x, int y, int z, int frontX, int frontY, int frontZ, double distance) {
    }

    private Raycast() {
    }

    static Hit cast(Voxels world, double ox, double oy, double oz, double dx, double dy, double dz, double reach) {
        int x = Body.floor(ox), y = Body.floor(oy), z = Body.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tMaxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? x + 1 - ox : ox - x) * tDeltaX;
        double tMaxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? y + 1 - oy : oy - y) * tDeltaY;
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? z + 1 - oz : oz - z) * tDeltaZ;
        int px = x, py = y, pz = z;
        double t = 0;
        while (t <= reach) {
            short id = world.id(x, y, z);
            if (id == Mesher.SOLID && y >= 0) {
                return null; // not loaded
            }
            if (id > 0 && id != Mesher.SOLID && !world.blocks.get(id - 1).liquid) {
                return new Hit(x, y, z, px, py, pz, t);
            }
            px = x;
            py = y;
            pz = z;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }
}
