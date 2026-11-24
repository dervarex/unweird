package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps a {@link Game3D} world between runs: every block you placed or broke, where you stood, the seed. Stored in
 * your user folder ({@code ~/.local/share/unweird-game/<GameClass>/} on Linux); delete the folder for a new world.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Saved {
}
