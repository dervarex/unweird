package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** How much an {@link Explorer} can take: {@code @Health(20)}. Falling more than about three blocks and {@link Monster monsters} hurt it; at zero it starts again where it began (or your {@code died()} method decides). {@code getHealth()} for your own HUD; hearts are shown above the block bar. Without it, nothing hurts. */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Health {
    int value();
}
