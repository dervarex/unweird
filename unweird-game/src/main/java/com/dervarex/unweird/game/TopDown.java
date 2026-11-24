package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Seen from above instead of from the side: no gravity, the {@link Player} walks in all directions,
 * {@link Enemy enemies} come after the player. Put it on your {@link Game}.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TopDown {
}
