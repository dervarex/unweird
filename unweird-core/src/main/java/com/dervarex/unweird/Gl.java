package com.dervarex.unweird;

/**
 * The OpenGL calls that work the same on desktop (OpenGL 3.3) and Android (OpenGL ES 3.0), for drawing inside
 * {@link Canvas#gl}. Get it with {@link Canvas#openGl()}. Names drop the {@code gl} prefix; constants keep GL's
 * names and values. Shaders start with {@link #shaderVersion()} so the same source compiles on both.
 * A different instance than last frame means a new GL context (Android can lose it in the background): create
 * your programs, buffers and textures again.
 *
 * <pre>{@code
 * Gl gl = canvas.openGl();
 * canvas.gl(this, x, y, w, h, 8, (pw, ph) -> {
 *     gl.clearColor(0, 0, 0, 1);
 *     gl.clear(Gl.COLOR_BUFFER_BIT);
 * });
 * }</pre>
 */
public interface Gl {

    int DEPTH_BUFFER_BIT = 0x0100;
    int STENCIL_BUFFER_BIT = 0x0400;
    int COLOR_BUFFER_BIT = 0x4000;
    int FALSE = 0;
    int TRUE = 1;
    int TRIANGLES = 0x0004;
    int LINES = 0x0001;
    int NEVER = 0x0200;
    int LESS = 0x0201;
    int LEQUAL = 0x0203;
    int ZERO = 0;
    int ONE = 1;
    int SRC_ALPHA = 0x0302;
    int ONE_MINUS_SRC_ALPHA = 0x0303;
    int FRONT = 0x0404;
    int BACK = 0x0405;
    int CULL_FACE = 0x0B44;
    int DEPTH_TEST = 0x0B71;
    int BLEND = 0x0BE2;
    int SCISSOR_TEST = 0x0C11;
    int UNPACK_ALIGNMENT = 0x0CF5;
    int TEXTURE_2D = 0x0DE1;
    int UNSIGNED_BYTE = 0x1401;
    int UNSIGNED_INT = 0x1405;
    int FLOAT = 0x1406;
    int RGBA = 0x1908;
    int RGBA8 = 0x8058;
    int NEAREST = 0x2600;
    int LINEAR = 0x2601;
    int LINEAR_MIPMAP_LINEAR = 0x2703;
    int TEXTURE_MAG_FILTER = 0x2800;
    int TEXTURE_MIN_FILTER = 0x2801;
    int TEXTURE_WRAP_S = 0x2802;
    int TEXTURE_WRAP_T = 0x2803;
    int REPEAT = 0x2901;
    int CLAMP_TO_EDGE = 0x812F;
    int TEXTURE0 = 0x84C0;
    int ARRAY_BUFFER = 0x8892;
    int ELEMENT_ARRAY_BUFFER = 0x8893;
    int STATIC_DRAW = 0x88E4;
    int DYNAMIC_DRAW = 0x88E8;
    int FRAGMENT_SHADER = 0x8B30;
    int VERTEX_SHADER = 0x8B31;
    int COMPILE_STATUS = 0x8B81;
    int LINK_STATUS = 0x8B82;
    int FRAMEBUFFER = 0x8D40;
    int READ_FRAMEBUFFER = 0x8CA8;
    int DRAW_FRAMEBUFFER = 0x8CA9;
    int RENDERBUFFER = 0x8D41;
    int COLOR_ATTACHMENT0 = 0x8CE0;
    int DEPTH_STENCIL_ATTACHMENT = 0x821A;
    int DEPTH24_STENCIL8 = 0x88F0;
    int FRAMEBUFFER_COMPLETE = 0x8CD5;
    int MAX_SAMPLES = 0x8D57;

    /**
     * The first lines of every shader: {@code #version 330 core} on desktop, {@code #version 300 es} with default
     * precisions on Android. Write the rest in the common subset (in/out, {@code texture()}, no {@code gl_FragColor}).
     */
    String shaderVersion();

    // ---- state ----

    void enable(int capability);

    void disable(int capability);

    void depthMask(boolean write);

    void depthFunc(int function);

    void colorMask(boolean r, boolean g, boolean b, boolean a);

    void blendFunc(int source, int destination);

    void cullFace(int face);

    void viewport(int x, int y, int width, int height);

    void clearColor(float r, float g, float b, float a);

    void clearDepth(float depth);

    void clear(int mask);

    int getInteger(int name);

    // ---- shaders ----

    int createShader(int type);

    void shaderSource(int shader, String source);

    void compileShader(int shader);

    int getShaderi(int shader, int name);

    String getShaderInfoLog(int shader);

    void deleteShader(int shader);

    int createProgram();

    void attachShader(int program, int shader);

    void linkProgram(int program);

    int getProgrami(int program, int name);

    String getProgramInfoLog(int program);

    void useProgram(int program);

    void deleteProgram(int program);

    int getUniformLocation(int program, String name);

    void uniform1i(int location, int value);

    void uniform1f(int location, float value);

    void uniform2f(int location, float x, float y);

    void uniform3f(int location, float x, float y, float z);

    void uniform4f(int location, float x, float y, float z, float w);

    /** Column-major, like JOML's {@code Matrix4f.get(float[])}. */
    void uniformMatrix4fv(int location, float[] matrix);

    void uniformMatrix3fv(int location, float[] matrix);

    // ---- buffers ----

    int genVertexArray();

    void bindVertexArray(int array);

    void deleteVertexArray(int array);

    int genBuffer();

    void bindBuffer(int target, int buffer);

    void bufferData(int target, float[] data, int usage);

    void bufferData(int target, int[] data, int usage);

    void deleteBuffer(int buffer);

    void enableVertexAttribArray(int index);

    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset);

    void drawArrays(int mode, int first, int count);

    void drawElements(int mode, int count, int type, int offset);

    // ---- textures ----

    int genTexture();

    void bindTexture(int target, int texture);

    void activeTexture(int unit);

    /** {@code pixels} may be null to only allocate. */
    void texImage2D(int target, int level, int internalFormat, int width, int height, int format, int type, byte[] pixels);

    void texParameteri(int target, int name, int value);

    void generateMipmap(int target);

    void pixelStorei(int name, int value);

    void deleteTexture(int texture);

    // ---- framebuffers ----

    int genFramebuffer();

    void bindFramebuffer(int target, int framebuffer);

    void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level);

    int checkFramebufferStatus(int target);

    void blitFramebuffer(int x0, int y0, int x1, int y1, int dx0, int dy0, int dx1, int dy1, int mask, int filter);

    void deleteFramebuffer(int framebuffer);

    int genRenderbuffer();

    void bindRenderbuffer(int target, int renderbuffer);

    void renderbufferStorage(int target, int internalFormat, int width, int height);

    void renderbufferStorageMultisample(int target, int samples, int internalFormat, int width, int height);

    void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer);

    void deleteRenderbuffer(int renderbuffer);
}
