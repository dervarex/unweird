package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How many pixels of your art fit on screen (default 320 × 180, i.e. 20 × 11 tiles of 16 pixels). The game is scaled
 * up to fill the window with sharp pixels, so it looks the same in every window size. {@code tile} is the size of one
 * tile in pixels: the unit of all positions (default 16).
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Resolution {
    int width();

    int height();

    int tile() default 16;
}
