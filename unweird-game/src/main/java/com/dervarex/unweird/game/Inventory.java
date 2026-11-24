package com.dervarex.unweird.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The {@link Explorer} has to collect blocks before building with them: breaking one adds it, placing one uses it up. The block bar shows only what it has, with counts. {@code give(block, 10)} hands out blocks. Without it, every block is unlimited. */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Inventory {
}
