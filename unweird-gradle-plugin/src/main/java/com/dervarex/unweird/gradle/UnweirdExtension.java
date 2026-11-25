package com.dervarex.unweird.gradle;

import org.gradle.api.provider.Property;

/**
 * Optional modules and the version, in {@code unweird { ... }}:
 *
 * <pre>{@code
 * unweird {
 *     threeD = true
 *     components = true
 * }
 * }</pre>
 */
public abstract class UnweirdExtension {

    /** The unweird version; defaults to the plugin's own. */
    public abstract Property<String> getVersion();

    /** {@code unweird-3d}: 3D views. */
    public abstract Property<Boolean> getThreeD();

    /** {@code unweird-components}: ready-made components such as {@code LoginForm}. */
    public abstract Property<Boolean> getComponents();

    /** {@code unweird-install}: browsing catalogs and installing content (desktop). */
    public abstract Property<Boolean> getInstall();
}
