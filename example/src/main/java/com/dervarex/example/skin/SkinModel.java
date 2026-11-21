package com.dervarex.example.skin;

import com.dervarex.unweird.scene3d.Material;
import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.scene3d.Node;
import com.dervarex.unweird.scene3d.Texture;

/**
 * A Minecraft-style player model from a 64×64 skin, built from unweird-3d boxes with box UVs.
 * Units are skin pixels; the feet stand on y = 0 and the player faces +Z (towards the default camera).
 */
public final class SkinModel {

    private static final float SKIN = 64;

    private final Texture skin;
    private final Node root = Node.group().name("player");
    private final Node rightArm = Node.group();
    private final Node leftArm = Node.group();
    private final Node rightLeg = Node.group().position(-2, 12, 0);
    private final Node leftLeg = Node.group().position(2, 12, 0);
    private final Node head = Node.group().position(0, 24, 0);
    private final Node body = Node.group();
    private boolean slim;
    private boolean overlay = true;

    public SkinModel(Texture skin) {
        this.skin = skin;
        root.add(head, body, rightArm, leftArm, rightLeg, leftLeg);
        build();
    }

    public Node root() {
        return root;
    }

    /** Slim (3 pixel, "Alex") or classic (4 pixel, "Steve") arms. */
    public void slim(boolean slim) {
        this.slim = slim;
        build();
    }

    /** The outer layer (hat, jacket, sleeves, trousers). */
    public void overlay(boolean visible) {
        overlay = visible;
        build();
    }

    /** Swings arms and legs; {@code walking = false} eases back to standing. */
    public void animate(double seconds, boolean walking) {
        float swing = walking ? (float) Math.sin(seconds * 6) * 35 : 0;
        rightArm.rotation(swing, 0, 0);
        leftArm.rotation(-swing, 0, 0);
        rightLeg.rotation(-swing, 0, 0);
        leftLeg.rotation(swing, 0, 0);
    }

    private void build() {
        float armWidth = slim ? 3 : 4;
        // shoulders are the pivots; the player's right is at -X because they face +Z
        rightArm.position(-(4 + armWidth / 2), 22, 0);
        leftArm.position(4 + armWidth / 2, 22, 0);

        part(head, 8, 8, 8, 4, 0, 0, 32, 0);               // head + hat
        part(body, 8, 12, 4, 0, 18, 16, 16, 16, 32);        // body + jacket
        part(rightArm, armWidth, 12, 4, -4, 40, 16, 40, 32);
        part(leftArm, armWidth, 12, 4, -4, 32, 48, 48, 48);
        part(rightLeg, 4, 12, 4, -6, 0, 16, 0, 32);
        part(leftLeg, 4, 12, 4, -6, 16, 48, 0, 48);
    }

    private void part(Node pivot, float w, float h, float d, float offsetY, float u, float v, float overlayU, float overlayV) {
        part(pivot, w, h, d, 0, offsetY, u, v, overlayU, overlayV);
    }

    /** One limb: the base box, and the outer layer half a pixel bigger, both hanging off {@code pivot}. */
    private void part(Node pivot, float w, float h, float d, float x, float offsetY, float u, float v, float overlayU, float overlayV) {
        for (Node child : pivot.children()) {
            pivot.remove(child);
        }
        Node base = new Node(Mesh.box(w, h, d, Mesh.BoxUv.of(u, v, w, h, d, SKIN, SKIN)), Material.texture(skin).cutout())
                .position(x, offsetY, 0);
        pivot.add(base);
        if (overlay) {
            Node outer = new Node(Mesh.box(w + 1, h + 1, d + 1, Mesh.BoxUv.of(overlayU, overlayV, w, h, d, SKIN, SKIN)),
                    Material.texture(skin).cutout().doubleSided())
                    .position(x, offsetY, 0);
            pivot.add(outer);
        }
    }
}
