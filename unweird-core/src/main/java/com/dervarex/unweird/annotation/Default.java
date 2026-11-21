package com.dervarex.unweird.annotation;

import com.dervarex.unweird.Side;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shows this component on every screen, docked to the given side.
 * The component is a single shared instance; reach it anywhere with {@code shared(MyComponent.class)}.
 * Screens opt out with {@link Without}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Default {
    Side value();
}
