package com.dervarex.unweird.scene3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * An orbit camera: looks at {@link #target} from {@code distance} away, turned by {@code yaw} (around Y) and
 * {@code pitch} (up/down), both in degrees. Yaw 0 looks from +Z towards -Z.
 *
 * <p>Or a first-person camera ({@link #firstPerson}): stands at a point and looks around from there; positive pitch
 * looks up.
 */
public final class Camera {

    final Vector3f target = new Vector3f();
    float distance = 5;
    float yaw;
    float pitch = 20;
    float fov = 45;
    float near = 0.05f;
    float far = 1000;
    float minDistance = 0.5f;
    float maxDistance = 500;
    boolean firstPerson;

    public static Camera orbit(float targetX, float targetY, float targetZ, float distance) {
        Camera camera = new Camera();
        camera.target.set(targetX, targetY, targetZ);
        camera.distance = distance;
        return camera;
    }

    /** A camera standing at (x, y, z), looking along -Z; turn it with {@link #angles}. */
    public static Camera firstPerson(float x, float y, float z) {
        Camera camera = new Camera();
        camera.firstPerson = true;
        camera.target.set(x, y, z);
        camera.pitch = 0;
        return camera;
    }

    /** Moves a first-person camera (or an orbit camera's target). */
    public Camera position(float x, float y, float z) {
        target.set(x, y, z);
        return this;
    }

    /** Whether this is a {@link #firstPerson} camera. */
    public boolean isFirstPerson() {
        return firstPerson;
    }

    /** The unit vector the camera looks along. */
    public Vector3f forward() {
        if (firstPerson) {
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            return new Vector3f((float) (-Math.sin(yawRad) * Math.cos(pitchRad)), (float) Math.sin(pitchRad),
                    (float) (-Math.cos(yawRad) * Math.cos(pitchRad)));
        }
        return new Vector3f(target).sub(eye()).normalize();
    }

    /** The far clipping distance (how far you can see), 1000 by default. */
    public Camera viewDistance(float far) {
        this.far = Math.max(near * 2, far);
        return this;
    }

    public Camera angles(float yawDegrees, float pitchDegrees) {
        yaw = yawDegrees;
        pitch = clampPitch(pitchDegrees);
        return this;
    }

    public Camera fov(float degrees) {
        fov = Math.max(5, Math.min(150, degrees));
        return this;
    }

    /** How close and far zooming may go. */
    public Camera zoomLimits(float min, float max) {
        minDistance = min;
        maxDistance = max;
        distance = Math.max(min, Math.min(max, distance));
        return this;
    }

    public Camera target(float x, float y, float z) {
        target.set(x, y, z);
        return this;
    }

    public Vector3f target() {
        return new Vector3f(target);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float distance() {
        return distance;
    }

    public Camera distance(float distance) {
        this.distance = Math.max(minDistance, Math.min(maxDistance, distance));
        return this;
    }

    /** Where the camera is. */
    public Vector3f eye() {
        if (firstPerson) {
            return new Vector3f(target);
        }
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        float x = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        float y = (float) Math.sin(pitchRad);
        float z = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        return new Vector3f(x, y, z).mul(distance).add(target);
    }

    Matrix4f view() {
        if (firstPerson) {
            return new Matrix4f().lookAt(target, new Vector3f(target).add(forward()), new Vector3f(0, 1, 0));
        }
        return new Matrix4f().lookAt(eye(), target, new Vector3f(0, 1, 0));
    }

    Matrix4f projection(float aspect) {
        return new Matrix4f().perspective((float) Math.toRadians(fov), Math.max(0.01f, aspect), near, far);
    }

    /** Turns the camera by mouse movement (degrees). */
    public void orbitBy(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch = clampPitch(pitch + dPitch);
    }

    /** Moves the target sideways/up in screen space; {@code dx}, {@code dy} as fractions of the view height. */
    void panBy(float dx, float dy) {
        Matrix4f view = view();
        Vector3f right = new Vector3f(view.m00(), view.m10(), view.m20());
        Vector3f up = new Vector3f(view.m01(), view.m11(), view.m21());
        float scale = 2 * distance * (float) Math.tan(Math.toRadians(fov) / 2);
        target.sub(right.mul(dx * scale)).add(up.mul(dy * scale));
    }

    void zoomBy(double steps) {
        distance(distance * (float) Math.pow(0.88, steps));
    }

    /** The ray from the eye through a point of a view {@code width × height} big (0,0 = top-left). */
    public Ray ray(float x, float y, float width, float height) {
        Matrix4f inverse = projection(width / height).mul(view()).invert();
        float ndcX = 2 * x / width - 1;
        float ndcY = 1 - 2 * y / height;
        Vector4f near4 = new Vector4f(ndcX, ndcY, -1, 1).mul(inverse);
        Vector4f far4 = new Vector4f(ndcX, ndcY, 1, 1).mul(inverse);
        Vector3f nearPoint = new Vector3f(near4.x, near4.y, near4.z).div(near4.w);
        Vector3f farPoint = new Vector3f(far4.x, far4.y, far4.z).div(far4.w);
        return new Ray(nearPoint, farPoint.sub(nearPoint).normalize());
    }

    private static float clampPitch(float degrees) {
        return Math.max(-89, Math.min(89, degrees));
    }
}
