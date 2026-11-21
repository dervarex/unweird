package com.dervarex.unweird.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field the UI depends on. Readability only: it changes nothing at runtime.
 * Lambdas such as {@code new Text(() -> "Clicked " + count)} are re-evaluated after every event anyway;
 * call {@code refresh()} after changing state from another thread.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface State {
}
