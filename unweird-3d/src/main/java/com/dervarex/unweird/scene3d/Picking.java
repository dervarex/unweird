package com.dervarex.unweird.scene3d;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Ray/mesh intersection (Möller–Trumbore), done in the mesh's own space. */
final class Picking {

    private static final float EPSILON = 1e-7f;

    private Picking() {
    }

    static Hit intersect(Node node, Mesh mesh, Matrix4f world, Ray ray) {
        Matrix4f inverse = new Matrix4f(world).invert();
        Vector3f origin = inverse.transformPosition(new Vector3f(ray.origin()));
        Vector3f direction = inverse.transformDirection(new Vector3f(ray.direction()));
        boolean twoSided = node.material().doubleSided;

        float bestT = Float.POSITIVE_INFINITY;
        Vector3f bestNormal = null;
        int[] indices = mesh.indices;
        for (int i = 0; i < indices.length; i += 3) {
            Vector3f a = mesh.position(indices[i]);
            Vector3f b = mesh.position(indices[i + 1]);
            Vector3f c = mesh.position(indices[i + 2]);
            Vector3f edge1 = new Vector3f(b).sub(a);
            Vector3f edge2 = new Vector3f(c).sub(a);
            Vector3f p = new Vector3f(direction).cross(edge2);
            float det = edge1.dot(p);
            if (Math.abs(det) < EPSILON || (!twoSided && det < 0)) {
                continue; // parallel, or the back of a one-sided face
            }
            float invDet = 1 / det;
            Vector3f s = new Vector3f(origin).sub(a);
            float u = s.dot(p) * invDet;
            if (u < 0 || u > 1) {
                continue;
            }
            Vector3f q = new Vector3f(s).cross(edge1);
            float v = direction.dot(q) * invDet;
            if (v < 0 || u + v > 1) {
                continue;
            }
            float t = edge2.dot(q) * invDet;
            if (t > EPSILON && t < bestT) {
                bestT = t;
                bestNormal = new Vector3f(edge1).cross(edge2);
                if (det < 0) {
                    bestNormal.negate();
                }
            }
        }
        if (bestNormal == null) {
            return null;
        }
        Vector3f localPoint = new Vector3f(direction).mul(bestT).add(origin);
        Vector3f point = world.transformPosition(localPoint);
        Vector3f normal = new Matrix3f(world).invert().transpose().transform(bestNormal).normalize();
        return new Hit(node, point, normal, point.distance(ray.origin()));
    }
}
