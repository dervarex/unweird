package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How many hits a {@link Player} takes before the level starts over: {@code @Lives(3)}. Each hit costs one and makes
 * it blink for a moment; {@code getLives()} tells how many are left (for a HUD). Without it, one hit restarts.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Lives {
    int value();
}
