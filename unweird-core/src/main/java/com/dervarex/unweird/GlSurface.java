package com.dervarex.unweird;

/**
 * An offscreen OpenGL target for {@link Canvas#gl}: a multisampled framebuffer to draw into, resolved into a
 * plain texture that NanoVG shows as an image. Written against {@link Gl}, so it works on desktop and Android.
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

    void render(Vg vg, Gl gl, int width, int height, long frame, GlPainter painter) {
        lastFrame = frame;
        if (width != this.width || height != this.height || texture == 0) {
            free(vg, gl);
            create(vg, gl, width, height);
        }
        gl.bindFramebuffer(Gl.FRAMEBUFFER, drawFramebuffer);
        gl.viewport(0, 0, width, height);
        gl.disable(Gl.SCISSOR_TEST);
        gl.depthMask(true);
        gl.colorMask(true, true, true, true);
        gl.clearColor(0, 0, 0, 0);
        gl.clearDepth(1);
        gl.clear(Gl.COLOR_BUFFER_BIT | Gl.DEPTH_BUFFER_BIT | Gl.STENCIL_BUFFER_BIT);
        try {
            painter.paint(width, height);
        } finally {
            // leave GL the way NanoVG expects it
            gl.bindVertexArray(0);
            gl.useProgram(0);
            gl.disable(Gl.DEPTH_TEST);
            gl.disable(Gl.CULL_FACE);
            gl.depthMask(true);
            gl.activeTexture(Gl.TEXTURE0);
            gl.bindTexture(Gl.TEXTURE_2D, 0);
            if (samples > 1) {
                gl.bindFramebuffer(Gl.READ_FRAMEBUFFER, drawFramebuffer);
                gl.bindFramebuffer(Gl.DRAW_FRAMEBUFFER, resolveFramebuffer);
                gl.blitFramebuffer(0, 0, width, height, 0, 0, width, height, Gl.COLOR_BUFFER_BIT, Gl.NEAREST);
            }
            gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
        }
    }

    private void create(Vg vg, Gl gl, int width, int height) {
        this.width = width;
        this.height = height;
        samples = Math.min(SAMPLES, gl.getInteger(Gl.MAX_SAMPLES));

        texture = gl.genTexture();
        gl.bindTexture(Gl.TEXTURE_2D, texture);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, width, height, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, Gl.LINEAR);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MAG_FILTER, Gl.LINEAR);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_S, Gl.CLAMP_TO_EDGE);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_T, Gl.CLAMP_TO_EDGE);
        gl.bindTexture(Gl.TEXTURE_2D, 0);

        resolveFramebuffer = gl.genFramebuffer();
        gl.bindFramebuffer(Gl.FRAMEBUFFER, resolveFramebuffer);
        gl.framebufferTexture2D(Gl.FRAMEBUFFER, Gl.COLOR_ATTACHMENT0, Gl.TEXTURE_2D, texture, 0);

        if (samples > 1) {
            drawFramebuffer = gl.genFramebuffer();
            gl.bindFramebuffer(Gl.FRAMEBUFFER, drawFramebuffer);
            colorBuffer = gl.genRenderbuffer();
            gl.bindRenderbuffer(Gl.RENDERBUFFER, colorBuffer);
            gl.renderbufferStorageMultisample(Gl.RENDERBUFFER, samples, Gl.RGBA8, width, height);
            gl.framebufferRenderbuffer(Gl.FRAMEBUFFER, Gl.COLOR_ATTACHMENT0, Gl.RENDERBUFFER, colorBuffer);
            depthBuffer = gl.genRenderbuffer();
            gl.bindRenderbuffer(Gl.RENDERBUFFER, depthBuffer);
            gl.renderbufferStorageMultisample(Gl.RENDERBUFFER, samples, Gl.DEPTH24_STENCIL8, width, height);
        } else {
            drawFramebuffer = resolveFramebuffer;
            depthBuffer = gl.genRenderbuffer();
            gl.bindRenderbuffer(Gl.RENDERBUFFER, depthBuffer);
            gl.renderbufferStorage(Gl.RENDERBUFFER, Gl.DEPTH24_STENCIL8, width, height);
        }
        gl.framebufferRenderbuffer(Gl.FRAMEBUFFER, Gl.DEPTH_STENCIL_ATTACHMENT, Gl.RENDERBUFFER, depthBuffer);
        int status = gl.checkFramebufferStatus(Gl.FRAMEBUFFER);
        gl.bindRenderbuffer(Gl.RENDERBUFFER, 0);
        gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
        if (status != Gl.FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("unweird: can't create an OpenGL surface (status 0x" + Integer.toHexString(status) + ")");
        }
        nvgImage = vg.imageFromTexture(texture, width, height);
    }

    void free(Vg vg, Gl gl) {
        if (nvgImage > 0) {
            vg.deleteImage(nvgImage);
            nvgImage = 0;
        }
        if (drawFramebuffer != 0 && drawFramebuffer != resolveFramebuffer) {
            gl.deleteFramebuffer(drawFramebuffer);
        }
        if (resolveFramebuffer != 0) {
            gl.deleteFramebuffer(resolveFramebuffer);
        }
        if (colorBuffer != 0) {
            gl.deleteRenderbuffer(colorBuffer);
        }
        if (depthBuffer != 0) {
            gl.deleteRenderbuffer(depthBuffer);
        }
        if (texture != 0) {
            gl.deleteTexture(texture);
        }
        drawFramebuffer = resolveFramebuffer = colorBuffer = depthBuffer = texture = 0;
        width = height = 0;
    }
}
