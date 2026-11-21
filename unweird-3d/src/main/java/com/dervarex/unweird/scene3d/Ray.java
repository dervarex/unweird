package com.dervarex.unweird.scene3d;

import org.joml.Vector3f;

/** A half-line: {@code origin + t * direction} for t ≥ 0. {@code direction} is normalized. */
public record Ray(Vector3f origin, Vector3f direction) {

    public Vector3f at(float t) {
        return new Vector3f(direction).mul(t).add(origin);
    }
}
