package com.dervarex.unweird.game;

/**
 * A kind of block in a {@link Game3D}. Declare one class per kind and one field per class:
 *
 * <pre>{@code
 * Grass grass = new Grass();
 * class Grass extends Block {}
 * @SeeThrough class Leaves extends Block {}
 * @Liquid class Water extends Block {}
 * @Plant class Flower extends Block {}
 * }</pre>
 *
 * Its look is found by name: {@code grass_top.png}, {@code grass_side.png}, {@code grass_bottom.png}, or
 * {@code grass.png} for all sides (16 × 16 pixels works well). Without pictures it gets a placeholder whose color
 * fits the name (grass, dirt, stone, sand, water, log, leaves, snow, glass, …).
 *
 * <p>The block fields are also what the {@link Explorer} can build with, in declaration order. Optional methods:
 * {@code broken()} and {@code placed()} run when the explorer breaks or places one.
 */
public class Block {

    /** 1, 2, 3, … in declaration order; 0 is air. Set when the game starts. */
    int id;

    final boolean seeThrough = getClass().isAnnotationPresent(SeeThrough.class);
    final boolean liquid = getClass().isAnnotationPresent(Liquid.class);
    final boolean plant = getClass().isAnnotationPresent(Plant.class);
    final boolean unbreakable = getClass().isAnnotationPresent(Unbreakable.class);

    /** Walls you can't walk through. */
    final boolean solid() {
        return !liquid && !plant;
    }

    /** Hides the faces of blocks next to it. */
    final boolean opaque() {
        return !seeThrough && !liquid && !plant;
    }

    /** The block's name, e.g. "Grass". */
    public final String name() {
        return Assets.words(getClass().getSimpleName());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
