package com.dervarex.unweird.game;

/**
 * A platform that moves back and forth and carries whatever stands on it:
 * {@code new Lift().at(10, 2).travel(0, 4)} goes 4 tiles up and back down, forever. Jump through it from below like a
 * {@link Platform}. {@link Speed @Speed} sets how fast (default 2 tiles per second).
 */
public class Mover extends Platform {

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.PLATFORM;
    }
}
