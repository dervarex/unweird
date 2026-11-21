package com.dervarex.unweird.scene3d;

import org.joml.Vector3f;

/**
 * Where a ray met a {@link Node}'s mesh.
 *
 * @param point    the point, in scene coordinates
 * @param normal   the surface's outward normal there, in scene coordinates (normalized)
 * @param distance from the ray's origin
 */
public record Hit(Node node, Vector3f point, Vector3f normal, float distance) {

    /** The integer block (voxel) that was hit, for block worlds with unit cubes. */
    public int[] block() {
        return new int[]{floor(point.x - normal.x * 0.01f), floor(point.y - normal.y * 0.01f), floor(point.z - normal.z * 0.01f)};
    }

    /** The integer block in front of the hit face (where a new block would go). */
    public int[] blockInFront() {
        return new int[]{floor(point.x + normal.x * 0.01f), floor(point.y + normal.y * 0.01f), floor(point.z + normal.z * 0.01f)};
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }
}
