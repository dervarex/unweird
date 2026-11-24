package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The background color as hex RGB, e.g. {@code @Sky(0x1b1f3a)} for night. On a {@link Game}, {@link Level} or
 * {@link Game3D}. A picture named {@code background.png} (or after the level: {@code meadow_background.png}) is drawn
 * over it in 2D.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Sky {
    int value();
}
