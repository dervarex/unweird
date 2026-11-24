package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.style.Color;

import java.util.Objects;

/**
 * How a {@link Node} looks: a color, optionally a texture (multiplied), lit by the scene's light or flat.
 * Transparency: {@link #cutout()} for pixel art with holes (skin overlays), {@link #transparent()} for glass-like blending.
 */
public final class Material {

    /** The color or tint (white for plain textures). */
    @lombok.Getter Color color = Color.WHITE;
    Texture texture;
    boolean lit = true;
    float cutoff;
    boolean transparent;
    boolean doubleSided;

    public static Material color(Color color) {
        return new Material().tint(color);
    }

    public static Material texture(Texture texture) {
        Material material = new Material();
        material.texture = Objects.requireNonNull(texture, "texture");
        return material;
    }

    /** Multiplies everything (texture and vertex colors) by this color. */
    public Material tint(Color color) {
        this.color = Objects.requireNonNull(color, "color");
        return this;
    }

    /** Not affected by light: full brightness everywhere. */
    public Material unlit() {
        lit = false;
        return this;
    }

    /** Pixels with less than half opacity are not drawn at all (sharp holes, e.g. a skin's outer layer). */
    public Material cutout() {
        cutoff = 0.5f;
        return this;
    }

    /** Blended with what's behind it. Transparent nodes are drawn after opaque ones, back to front. */
    public Material transparent() {
        transparent = true;
        return this;
    }

    /** Draws back faces too (thin planes, insides of boxes). */
    public Material doubleSided() {
        doubleSided = true;
        return this;
    }
}
