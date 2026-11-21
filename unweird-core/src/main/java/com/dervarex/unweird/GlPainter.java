package com.dervarex.unweird;

/**
 * Draws with raw OpenGL into an offscreen surface (see {@link Canvas#gl}). Runs on the UI thread with the surface
 * bound, the viewport set to it and color + depth cleared to transparent. Leave GL state as you like; unweird
 * restores what it needs afterwards.
 */
@FunctionalInterface
public interface GlPainter {

    /** @param width  surface width in physical pixels
     *  @param height surface height in physical pixels */
    void paint(int width, int height);
}
