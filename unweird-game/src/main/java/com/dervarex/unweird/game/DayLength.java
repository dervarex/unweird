package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Days and nights in a {@link Game3D}: one full day takes this many minutes (e.g. {@code @DayLength(10)}). The sky,
 * the fog and the world get darker at night. Without it, it's always day.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DayLength {
    double value();
}
