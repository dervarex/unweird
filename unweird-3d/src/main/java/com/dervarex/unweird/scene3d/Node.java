package com.dervarex.unweird.scene3d;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Something in a {@link Scene}: an optional mesh with a material, a position, rotation and scale, and children that
 * move with it. To rotate a limb around its shoulder, put the shoulder in a parent node and offset the mesh in a child.
 *
 * <p>Change nodes on the UI thread (event handlers, animations); the next frame shows it.
 */
public class Node {

    private Mesh mesh;
    private Material material = new Material();
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f scale = new Vector3f(1);
    private final List<Node> children = new CopyOnWriteArrayList<>();
    private Node parent;
    private boolean visible = true;
    private String name;

    public Node() {
    }

    public Node(Mesh mesh, Material material) {
        this.mesh = mesh;
        this.material = Objects.requireNonNull(material, "material");
    }

    public static Node group() {
        return new Node();
    }

    public Node mesh(Mesh mesh) {
        this.mesh = mesh;
        return this;
    }

    public Mesh mesh() {
        return mesh;
    }

    public Node material(Material material) {
        this.material = Objects.requireNonNull(material, "material");
        return this;
    }

    public Material material() {
        return material;
    }

    public Node position(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    public Vector3f position() {
        return new Vector3f(position);
    }

    /** Sets the rotation from angles in degrees, applied Y (yaw), then X (pitch), then Z (roll). */
    public Node rotation(float xDegrees, float yDegrees, float zDegrees) {
        rotation.identity().rotateY((float) Math.toRadians(yDegrees)).rotateX((float) Math.toRadians(xDegrees)).rotateZ((float) Math.toRadians(zDegrees));
        return this;
    }

    public Node rotation(Quaternionf rotation) {
        this.rotation.set(rotation);
        return this;
    }

    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    public Node scale(float uniform) {
        scale.set(uniform);
        return this;
    }

    public Node scale(float x, float y, float z) {
        scale.set(x, y, z);
        return this;
    }

    public Node visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean visible() {
        return visible;
    }

    /** A name for finding it again or telling picks apart. */
    public Node name(String name) {
        this.name = name;
        return this;
    }

    public String name() {
        return name;
    }

    public Node add(Node... nodes) {
        for (Node node : nodes) {
            Objects.requireNonNull(node, "node");
            if (node.parent != null) {
                node.parent.remove(node);
            }
            node.parent = this;
            children.add(node);
        }
        return this;
    }

    public Node remove(Node node) {
        if (children.remove(node)) {
            node.parent = null;
        }
        return this;
    }

    public List<Node> children() {
        return List.copyOf(children);
    }

    /** This node's transform relative to its parent. */
    public Matrix4f localMatrix() {
        return new Matrix4f().translation(position).rotate(rotation).scale(scale);
    }

    /** This node's transform in the scene. */
    public Matrix4f worldMatrix() {
        Matrix4f matrix = localMatrix();
        for (Node p = parent; p != null; p = p.parent) {
            matrix = p.localMatrix().mul(matrix);
        }
        return matrix;
    }

    /** Visits this node and all visible descendants with their world matrices. */
    void walk(Matrix4f parentMatrix, Visitor visitor) {
        if (!visible) {
            return;
        }
        Matrix4f world = new Matrix4f(parentMatrix).mul(localMatrix());
        visitor.visit(this, world);
        for (Node child : children) {
            child.walk(world, visitor);
        }
    }

    interface Visitor {
        void visit(Node node, Matrix4f world);
    }

    /** Every node below this one, depth first. */
    public List<Node> descendants() {
        List<Node> all = new ArrayList<>();
        for (Node child : children) {
            all.add(child);
            all.addAll(child.descendants());
        }
        return all;
    }
}
