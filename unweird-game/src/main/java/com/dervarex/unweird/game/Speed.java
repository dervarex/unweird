package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How fast it moves, in tiles (2D) or blocks (3D) per second. Defaults: {@link Player} 7, {@link Enemy} 2,
 * {@link Shot} 14, {@link Explorer} 4.5, {@link Creature} 1.5.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Speed {
    float value();
}
