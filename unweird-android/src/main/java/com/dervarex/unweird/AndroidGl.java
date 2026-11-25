package com.dervarex.unweird;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link Gl} on OpenGL ES 3.0. The same constants as desktop OpenGL, so code using {@link Gl} runs on both.
 * One per GL context: a new instance tells renderers that their GL objects are gone.
 */
final class AndroidGl implements Gl {

    private final int[] one = new int[1];

    @Override
    public String shaderVersion() {
        return "#version 300 es\nprecision highp float;\nprecision highp int;\n";
    }

    @Override public void enable(int capability) { GLES30.glEnable(capability); }
    @Override public void disable(int capability) { GLES30.glDisable(capability); }
    @Override public void depthMask(boolean write) { GLES30.glDepthMask(write); }
    @Override public void depthFunc(int function) { GLES30.glDepthFunc(function); }
    @Override public void colorMask(boolean r, boolean g, boolean b, boolean a) { GLES30.glColorMask(r, g, b, a); }
    @Override public void blendFunc(int source, int destination) { GLES30.glBlendFunc(source, destination); }
    @Override public void cullFace(int face) { GLES30.glCullFace(face); }
    @Override public void viewport(int x, int y, int width, int height) { GLES30.glViewport(x, y, width, height); }
    @Override public void clearColor(float r, float g, float b, float a) { GLES30.glClearColor(r, g, b, a); }
    @Override public void clearDepth(float depth) { GLES30.glClearDepthf(depth); }
    @Override public void clear(int mask) { GLES30.glClear(mask); }

    @Override
    public int getInteger(int name) {
        GLES30.glGetIntegerv(name, one, 0);
        return one[0];
    }

    @Override public int createShader(int type) { return GLES30.glCreateShader(type); }
    @Override public void shaderSource(int shader, String source) { GLES30.glShaderSource(shader, source); }
    @Override public void compileShader(int shader) { GLES30.glCompileShader(shader); }

    @Override
    public int getShaderi(int shader, int name) {
        GLES30.glGetShaderiv(shader, name, one, 0);
        return one[0];
    }

    @Override public String getShaderInfoLog(int shader) { return GLES30.glGetShaderInfoLog(shader); }
    @Override public void deleteShader(int shader) { GLES30.glDeleteShader(shader); }
    @Override public int createProgram() { return GLES30.glCreateProgram(); }
    @Override public void attachShader(int program, int shader) { GLES30.glAttachShader(program, shader); }
    @Override public void linkProgram(int program) { GLES30.glLinkProgram(program); }

    @Override
    public int getProgrami(int program, int name) {
        GLES30.glGetProgramiv(program, name, one, 0);
        return one[0];
    }

    @Override public String getProgramInfoLog(int program) { return GLES30.glGetProgramInfoLog(program); }
    @Override public void useProgram(int program) { GLES30.glUseProgram(program); }
    @Override public void deleteProgram(int program) { GLES30.glDeleteProgram(program); }
    @Override public int getUniformLocation(int program, String name) { return GLES30.glGetUniformLocation(program, name); }
    @Override public void uniform1i(int location, int value) { GLES30.glUniform1i(location, value); }
    @Override public void uniform1f(int location, float value) { GLES30.glUniform1f(location, value); }
    @Override public void uniform2f(int location, float x, float y) { GLES30.glUniform2f(location, x, y); }
    @Override public void uniform3f(int location, float x, float y, float z) { GLES30.glUniform3f(location, x, y, z); }
    @Override public void uniform4f(int location, float x, float y, float z, float w) { GLES30.glUniform4f(location, x, y, z, w); }
    @Override public void uniformMatrix4fv(int location, float[] matrix) { GLES30.glUniformMatrix4fv(location, 1, false, matrix, 0); }
    @Override public void uniformMatrix3fv(int location, float[] matrix) { GLES30.glUniformMatrix3fv(location, 1, false, matrix, 0); }

    @Override
    public int genVertexArray() {
        GLES30.glGenVertexArrays(1, one, 0);
        return one[0];
    }

    @Override public void bindVertexArray(int array) { GLES30.glBindVertexArray(array); }

    @Override
    public void deleteVertexArray(int array) {
        one[0] = array;
        GLES30.glDeleteVertexArrays(1, one, 0);
    }

    @Override
    public int genBuffer() {
        GLES30.glGenBuffers(1, one, 0);
        return one[0];
    }

    @Override public void bindBuffer(int target, int buffer) { GLES30.glBindBuffer(target, buffer); }

    @Override
    public void bufferData(int target, float[] data, int usage) {
        ByteBuffer buffer = direct(data.length * 4);
        buffer.asFloatBuffer().put(data);
        GLES30.glBufferData(target, data.length * 4, buffer, usage);
    }

    @Override
    public void bufferData(int target, int[] data, int usage) {
        ByteBuffer buffer = direct(data.length * 4);
        buffer.asIntBuffer().put(data);
        GLES30.glBufferData(target, data.length * 4, buffer, usage);
    }

    @Override
    public void deleteBuffer(int buffer) {
        one[0] = buffer;
        GLES30.glDeleteBuffers(1, one, 0);
    }

    @Override public void enableVertexAttribArray(int index) { GLES30.glEnableVertexAttribArray(index); }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
        GLES30.glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    @Override public void drawArrays(int mode, int first, int count) { GLES30.glDrawArrays(mode, first, count); }
    @Override public void drawElements(int mode, int count, int type, int offset) { GLES30.glDrawElements(mode, count, type, offset); }

    @Override
    public int genTexture() {
        GLES30.glGenTextures(1, one, 0);
        return one[0];
    }

    @Override public void bindTexture(int target, int texture) { GLES30.glBindTexture(target, texture); }
    @Override public void activeTexture(int unit) { GLES30.glActiveTexture(unit); }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int format, int type, byte[] pixels) {
        ByteBuffer buffer = null;
        if (pixels != null) {
            buffer = direct(pixels.length);
            buffer.put(pixels).flip();
        }
        GLES30.glTexImage2D(target, level, internalFormat, width, height, 0, format, type, buffer);
    }

    @Override public void texParameteri(int target, int name, int value) { GLES30.glTexParameteri(target, name, value); }
    @Override public void generateMipmap(int target) { GLES30.glGenerateMipmap(target); }
    @Override public void pixelStorei(int name, int value) { GLES30.glPixelStorei(name, value); }

    @Override
    public void deleteTexture(int texture) {
        one[0] = texture;
        GLES30.glDeleteTextures(1, one, 0);
    }

    @Override
    public int genFramebuffer() {
        GLES30.glGenFramebuffers(1, one, 0);
        return one[0];
    }

    @Override public void bindFramebuffer(int target, int framebuffer) { GLES30.glBindFramebuffer(target, framebuffer); }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        GLES30.glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
    }

    @Override public int checkFramebufferStatus(int target) { return GLES30.glCheckFramebufferStatus(target); }

    @Override
    public void blitFramebuffer(int x0, int y0, int x1, int y1, int dx0, int dy0, int dx1, int dy1, int mask, int filter) {
        GLES30.glBlitFramebuffer(x0, y0, x1, y1, dx0, dy0, dx1, dy1, mask, filter);
    }

    @Override
    public void deleteFramebuffer(int framebuffer) {
        one[0] = framebuffer;
        GLES30.glDeleteFramebuffers(1, one, 0);
    }

    @Override
    public int genRenderbuffer() {
        GLES30.glGenRenderbuffers(1, one, 0);
        return one[0];
    }

    @Override public void bindRenderbuffer(int target, int renderbuffer) { GLES30.glBindRenderbuffer(target, renderbuffer); }

    @Override
    public void renderbufferStorage(int target, int internalFormat, int width, int height) {
        GLES30.glRenderbufferStorage(target, internalFormat, width, height);
    }

    @Override
    public void renderbufferStorageMultisample(int target, int samples, int internalFormat, int width, int height) {
        GLES30.glRenderbufferStorageMultisample(target, samples, internalFormat, width, height);
    }

    @Override
    public void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        GLES30.glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
    }

    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        one[0] = renderbuffer;
        GLES30.glDeleteRenderbuffers(1, one, 0);
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
}
