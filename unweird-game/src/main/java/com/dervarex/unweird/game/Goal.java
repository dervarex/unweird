package com.dervarex.unweird.game;

/**
 * The end of a level: when the {@link Player} touches it, the next level starts. After the last one your game's
 * {@code won()} method runs (by default: a "You won!" message, then back to the first level).
 */
public class Goal extends Sprite {

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.GOAL;
    }

    @Override
    void touched(Sprite other) {
        if (other instanceof Player) {
            Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()), "win");
            nextLevel();
        }
    }
}
