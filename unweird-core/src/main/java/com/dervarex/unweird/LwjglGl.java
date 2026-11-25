package com.dervarex.unweird;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/** {@link Gl} on LWJGL's OpenGL 3.3 core profile. Desktop. */
final class LwjglGl implements Gl {

    static final LwjglGl INSTANCE = new LwjglGl();

    private LwjglGl() {
    }

    @Override
    public String shaderVersion() {
        return "#version 330 core\n";
    }

    @Override public void enable(int capability) { glEnable(capability); }
    @Override public void disable(int capability) { glDisable(capability); }
    @Override public void depthMask(boolean write) { glDepthMask(write); }
    @Override public void depthFunc(int function) { glDepthFunc(function); }
    @Override public void colorMask(boolean r, boolean g, boolean b, boolean a) { glColorMask(r, g, b, a); }
    @Override public void blendFunc(int source, int destination) { glBlendFunc(source, destination); }
    @Override public void cullFace(int face) { glCullFace(face); }
    @Override public void viewport(int x, int y, int width, int height) { glViewport(x, y, width, height); }
    @Override public void clearColor(float r, float g, float b, float a) { glClearColor(r, g, b, a); }
    @Override public void clearDepth(float depth) { glClearDepth(depth); }
    @Override public void clear(int mask) { glClear(mask); }
    @Override public int getInteger(int name) { return glGetInteger(name); }

    @Override public int createShader(int type) { return glCreateShader(type); }
    @Override public void shaderSource(int shader, String source) { glShaderSource(shader, source); }
    @Override public void compileShader(int shader) { glCompileShader(shader); }
    @Override public int getShaderi(int shader, int name) { return glGetShaderi(shader, name); }
    @Override public String getShaderInfoLog(int shader) { return glGetShaderInfoLog(shader); }
    @Override public void deleteShader(int shader) { glDeleteShader(shader); }
    @Override public int createProgram() { return glCreateProgram(); }
    @Override public void attachShader(int program, int shader) { glAttachShader(program, shader); }
    @Override public void linkProgram(int program) { glLinkProgram(program); }
    @Override public int getProgrami(int program, int name) { return glGetProgrami(program, name); }
    @Override public String getProgramInfoLog(int program) { return glGetProgramInfoLog(program); }
    @Override public void useProgram(int program) { glUseProgram(program); }
    @Override public void deleteProgram(int program) { glDeleteProgram(program); }
    @Override public int getUniformLocation(int program, String name) { return glGetUniformLocation(program, name); }
    @Override public void uniform1i(int location, int value) { glUniform1i(location, value); }
    @Override public void uniform1f(int location, float value) { glUniform1f(location, value); }
    @Override public void uniform2f(int location, float x, float y) { glUniform2f(location, x, y); }
    @Override public void uniform3f(int location, float x, float y, float z) { glUniform3f(location, x, y, z); }
    @Override public void uniform4f(int location, float x, float y, float z, float w) { glUniform4f(location, x, y, z, w); }
    @Override public void uniformMatrix4fv(int location, float[] matrix) { glUniformMatrix4fv(location, false, matrix); }
    @Override public void uniformMatrix3fv(int location, float[] matrix) { glUniformMatrix3fv(location, false, matrix); }

    @Override public int genVertexArray() { return glGenVertexArrays(); }
    @Override public void bindVertexArray(int array) { glBindVertexArray(array); }
    @Override public void deleteVertexArray(int array) { glDeleteVertexArrays(array); }
    @Override public int genBuffer() { return glGenBuffers(); }
    @Override public void bindBuffer(int target, int buffer) { glBindBuffer(target, buffer); }
    @Override public void bufferData(int target, float[] data, int usage) { glBufferData(target, data, usage); }
    @Override public void bufferData(int target, int[] data, int usage) { glBufferData(target, data, usage); }
    @Override public void deleteBuffer(int buffer) { glDeleteBuffers(buffer); }
    @Override public void enableVertexAttribArray(int index) { glEnableVertexAttribArray(index); }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
        glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    @Override public void drawArrays(int mode, int first, int count) { glDrawArrays(mode, first, count); }
    @Override public void drawElements(int mode, int count, int type, int offset) { glDrawElements(mode, count, type, offset); }

    @Override public int genTexture() { return glGenTextures(); }
    @Override public void bindTexture(int target, int texture) { glBindTexture(target, texture); }
    @Override public void activeTexture(int unit) { glActiveTexture(unit); }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int format, int type, byte[] pixels) {
        if (pixels == null) {
            glTexImage2D(target, level, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
            return;
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length).put(pixels).flip();
        try {
            glTexImage2D(target, level, internalFormat, width, height, 0, format, type, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Override public void texParameteri(int target, int name, int value) { glTexParameteri(target, name, value); }
    @Override public void generateMipmap(int target) { glGenerateMipmap(target); }
    @Override public void pixelStorei(int name, int value) { glPixelStorei(name, value); }
    @Override public void deleteTexture(int texture) { glDeleteTextures(texture); }

    @Override public int genFramebuffer() { return glGenFramebuffers(); }
    @Override public void bindFramebuffer(int target, int framebuffer) { glBindFramebuffer(target, framebuffer); }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
    }

    @Override public int checkFramebufferStatus(int target) { return glCheckFramebufferStatus(target); }

    @Override
    public void blitFramebuffer(int x0, int y0, int x1, int y1, int dx0, int dy0, int dx1, int dy1, int mask, int filter) {
        glBlitFramebuffer(x0, y0, x1, y1, dx0, dy0, dx1, dy1, mask, filter);
    }

    @Override public void deleteFramebuffer(int framebuffer) { glDeleteFramebuffers(framebuffer); }
    @Override public int genRenderbuffer() { return glGenRenderbuffers(); }
    @Override public void bindRenderbuffer(int target, int renderbuffer) { glBindRenderbuffer(target, renderbuffer); }

    @Override
    public void renderbufferStorage(int target, int internalFormat, int width, int height) {
        glRenderbufferStorage(target, internalFormat, width, height);
    }

    @Override
    public void renderbufferStorageMultisample(int target, int samples, int internalFormat, int width, int height) {
        glRenderbufferStorageMultisample(target, samples, internalFormat, width, height);
    }

    @Override
    public void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
    }

    @Override public void deleteRenderbuffer(int renderbuffer) { glDeleteRenderbuffers(renderbuffer); }
}
