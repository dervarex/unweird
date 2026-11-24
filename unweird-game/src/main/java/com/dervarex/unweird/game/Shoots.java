package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@link Player} shoots these with X or Ctrl, in the direction it faces: {@code @Shoots(Fireball.class)}
 * where {@code class Fireball extends Shot {}}.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Shoots {
    Class<? extends Shot> value();
}
