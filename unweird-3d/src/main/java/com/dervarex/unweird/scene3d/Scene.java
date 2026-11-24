package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.style.Color;
import org.joml.Vector3f;

/**
 * What a {@link View3D} shows: a tree of {@link Node}s, a light from one direction ("sun") plus ambient light,
 * and a background (transparent by default, so the UI shows through).
 */
public final class Scene {

    private final Node root = Node.group();
    Color background;
    final Vector3f sunDirection = new Vector3f(-0.4f, -1f, -0.6f).normalize();
    float ambient = 0.55f;
    Color fog;
    float fogStart;
    float fogEnd;

    public Scene add(Node... nodes) {
        root.add(nodes);
        return this;
    }

    public Scene remove(Node node) {
        root.remove(node);
        return this;
    }

    public Node root() {
        return root;
    }

    /** The nearest mesh surface along {@code ray}, or null. Invisible nodes are skipped. */
    public Hit pick(Ray ray) {
        Hit[] best = {null};
        root.walk(new org.joml.Matrix4f(), (node, world) -> {
            Mesh mesh = node.mesh();
            if (mesh == null || mesh.triangleCount() == 0) {
                return;
            }
            Hit hit = Picking.intersect(node, mesh, world, ray);
            if (hit != null && (best[0] == null || hit.distance() < best[0].distance())) {
                best[0] = hit;
            }
        });
        return best[0];
    }

    /** Fills the view with a color; null (the default) is transparent. */
    public Scene background(Color color) {
        this.background = color;
        return this;
    }

    /** Direction the light travels, e.g. (0, -1, 0) straight down. */
    public Scene sun(float x, float y, float z) {
        sunDirection.set(x, y, z).normalize();
        return this;
    }

    /**
     * Fades things into {@code color} between {@code start} and {@code end} units from the camera, so the edge of
     * what's drawn isn't a hard line. Null turns it off.
     */
    public Scene fog(Color color, float start, float end) {
        this.fog = color;
        this.fogStart = start;
        this.fogEnd = Math.max(start + 0.001f, end);
        return this;
    }

    /** How bright surfaces facing away from the sun are, 0..1. */
    public Scene ambient(float ambient) {
        this.ambient = Math.max(0, Math.min(1, ambient));
        return this;
    }
}
