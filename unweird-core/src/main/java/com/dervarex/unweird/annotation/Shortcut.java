package com.dervarex.unweird.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated method (no parameters) when the keys are pressed while its screen is shown,
 * e.g. {@code @Shortcut("Ctrl+S") void save()}. Works on screens and components (including docked ones).
 * Several patterns may be given: {@code @Shortcut({"F5", "Ctrl+R"})}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Shortcut {
    String[] value();
}
