package com.dervarex.unweird.game;

/** Hurts the {@link Player} on touch: spikes, lava, saws. By default that starts the level over (see {@link Player}). */
public class Hazard extends Sprite {

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.HAZARD;
    }

    @Override
    void touched(Sprite other) {
        if (other instanceof Player player) {
            player.damage();
        }
    }
}
