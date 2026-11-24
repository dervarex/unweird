package com.dervarex.unweird.game;

/**
 * Solid: nothing walks or falls through it. Ground, bricks, platforms you can't jump through.
 * {@code new Ground().from(0, 0).to(40, 0)} makes one long wall with the picture repeated.
 */
public class Wall extends Sprite {

    @Override
    boolean blockedByWalls() {
        return false;
    }

    @Override
    int layer() {
        return 0;
    }

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.WALL;
    }
}
