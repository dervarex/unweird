package com.dervarex.unweird.game;

/**
 * One level of a 2D {@link Game}: declare what's in it as fields, placed on the world's grid.
 *
 * <pre>{@code
 * class Meadow extends Level {
 *     Ground floor = new Ground().from(0, 0).to(60, 0);
 *     Coin coins   = new Coin().from(8, 3).to(12, 3);
 *     Spike spike  = new Spike().at(20, 1);
 *     Flag flag    = new Flag().at(58, 1);
 * }
 * }</pre>
 *
 * List the levels as fields of your game, in playing order: {@code Meadow meadow = new Meadow();}. A level starts
 * fresh every time it's played (coins are back after a restart); things declared on the game itself (often the
 * player) are carried from level to level and put back at their start position.
 *
 * <p>Optional methods: {@code started()} when the level begins, {@code tick()} every frame. A sound file named
 * after the level ({@code meadow.ogg}) plays as its music.
 */
public abstract class Level {

    /** Played already: the next time it's played, a fresh instance is made. */
    boolean used;
}
