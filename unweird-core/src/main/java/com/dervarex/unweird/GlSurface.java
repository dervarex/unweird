package com.dervarex.unweird;

import static org.lwjgl.nanovg.NanoVG.NVG_IMAGE_FLIPY;
import static org.lwjgl.nanovg.NanoVG.NVG_IMAGE_PREMULTIPLIED;
import static org.lwjgl.nanovg.NanoVG.nvgDeleteImage;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_IMAGE_NODELETE;
import static org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle;
import static org.lwjgl.opengl.GL33C.*;

/**
 * An offscreen OpenGL target for {@link Canvas#gl}: a multisampled framebuffer to draw into, resolved into a
 * plain texture that NanoVG shows as an image.
 */
final class GlSurface {

    private static final int SAMPLES = 4;

    private int width;
    private int height;
    private int samples;
    private int drawFramebuffer;
    private int colorBuffer;
    private int depthBuffer;
    private int resolveFramebuffer;
    private int texture;
    int nvgImage;
    long lastFrame;

    void render(long vg, int width, int height, long frame, GlPainter painter) {
        lastFrame = frame;
        if (width != this.width || height != this.height || texture == 0) {
            free(vg);
            create(vg, width, height);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, drawFramebuffer);
        glViewport(0, 0, width, height);
        glDisable(GL_SCISSOR_TEST);
        glDepthMask(true);
        glColorMask(true, true, true, true);
        glClearColor(0, 0, 0, 0);
        glClearDepth(1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        try {
            painter.paint(width, height);
        } finally {
            // leave GL the way NanoVG expects it
            glBindVertexArray(0);
            glUseProgram(0);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDepthMask(true);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, 0);
            if (samples > 1) {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, drawFramebuffer);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
                glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    private void create(long vg, int width, int height) {
        this.width = width;
        this.height = height;
        samples = Math.min(SAMPLES, glGetInteger(GL_MAX_SAMPLES));

        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        resolveFramebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, resolveFramebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);

        if (samples > 1) {
            drawFramebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, drawFramebuffer);
            colorBuffer = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, colorBuffer);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, width, height);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorBuffer);
            depthBuffer = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH24_STENCIL8, width, height);
        } else {
            drawFramebuffer = resolveFramebuffer;
            depthBuffer = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        }
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBuffer);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("unweird: can't create an OpenGL surface (status 0x" + Integer.toHexString(status) + ")");
        }
        // antialiased edges over a transparent background come out premultiplied
        nvgImage = nvglCreateImageFromHandle(vg, texture, width, height, NVG_IMAGE_NODELETE | NVG_IMAGE_FLIPY | NVG_IMAGE_PREMULTIPLIED);
    }

    void free(long vg) {
        if (nvgImage > 0) {
            nvgDeleteImage(vg, nvgImage);
            nvgImage = 0;
        }
        if (drawFramebuffer != 0 && drawFramebuffer != resolveFramebuffer) {
            glDeleteFramebuffers(drawFramebuffer);
        }
        if (resolveFramebuffer != 0) {
            glDeleteFramebuffers(resolveFramebuffer);
        }
        if (colorBuffer != 0) {
            glDeleteRenderbuffers(colorBuffer);
        }
        if (depthBuffer != 0) {
            glDeleteRenderbuffers(depthBuffer);
        }
        if (texture != 0) {
            glDeleteTextures(texture);
        }
        drawFramebuffer = resolveFramebuffer = colorBuffer = depthBuffer = texture = 0;
        width = height = 0;
    }
}
