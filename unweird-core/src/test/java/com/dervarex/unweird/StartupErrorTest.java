package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_API_UNAVAILABLE;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM_UNAVAILABLE;
import static org.lwjgl.glfw.GLFW.GLFW_VERSION_UNAVAILABLE;

class StartupErrorTest {

    @Test
    void missingLibGlPointsAtTheLikelyCause() {
        String message = Window.explainFailure(GLFW_API_UNAVAILABLE, "GLX: Failed to load GLX", true);
        assertTrue(message.contains("couldn't load OpenGL"), message);
        assertTrue(message.contains("GLX: Failed to load GLX"), message);
        // /run/opengl-driver/lib alone doesn't work: it has the vendor libraries but not libGL.so.1 itself
        assertTrue(message.contains("NixOS") && message.contains("LD_LIBRARY_PATH=/run/current-system/sw/share/nix-ld/lib"), message);
    }

    @Test
    void oldDriversAndMissingDisplaysGetTheirOwnMessage() {
        assertTrue(Window.explainFailure(GLFW_VERSION_UNAVAILABLE, "GLX: Failed to create context", true).contains("OpenGL 3.3"));
        String noDisplay = Window.explainFailure(GLFW_PLATFORM_UNAVAILABLE, "X11: Failed to open display :0", false);
        assertTrue(noDisplay.contains("no display") && noDisplay.contains("DISPLAY"), noDisplay);
    }
}
