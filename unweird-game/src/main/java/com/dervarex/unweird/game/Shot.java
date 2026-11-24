package com.dervarex.unweird.game;

/**
 * Flies straight until it hits a wall or an {@link Enemy} (which it defeats). The {@link Player} fires them with
 * {@link Shoots @Shoots}; {@link Speed @Speed} sets how fast (default 14 tiles per second).
 */
public class Shot extends Sprite {

    private static final float LIFETIME = 3;

    Sprite shooter;
    private float age;

    @Override
    int layer() {
        return 5;
    }

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.SHOT;
    }

    @Override
    void behave(World world, float dt) {
        age += dt;
        if (age > LIFETIME) {
            remove();
        }
    }

    @Override
    void bumped(Sprite wall, boolean horizontal) {
        remove();
    }

    @Override
    void touched(Sprite other) {
        if (other instanceof Enemy enemy && !removed) {
            enemy.defeat();
            remove();
        }
    }
}
