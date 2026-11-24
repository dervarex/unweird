package com.dervarex.unweird.game;

/**
 * Collected when the {@link Player} touches it: it disappears, plays a sound ({@code coin.ogg} for a {@code Coin},
 * otherwise a built-in one) and calls your {@code picked()} method if there is one:
 *
 * <pre>{@code
 * class Coin extends Pickup {
 *     void picked() { coins++; }
 * }
 * }</pre>
 */
public class Pickup extends Sprite {

    @Override
    int layer() {
        return 2;
    }

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.PICKUP;
    }

    @Override
    void touched(Sprite other) {
        if (other instanceof Player && !removed) {
            remove();
            Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()), "pickup");
            Hooks.of(getClass()).call(this, "picked");
        }
    }
}
